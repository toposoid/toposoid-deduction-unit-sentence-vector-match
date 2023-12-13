/*
 * Copyright 2021 Linked Ideal LLC.[https://linked-ideal.com/]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.{CLAIM, LOCAL, PREDICATE_ARGUMENT, ToposoidUtils}
import com.ideal.linked.toposoid.deduction.common.FacadeForAccessNeo4J.{getAnalyzedSentenceObjectBySentenceId, getCypherQueryResult}
import com.ideal.linked.toposoid.deduction.common.{DeductionUnitController, SentenceInfo}
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.{FeatureVectorIdentifier, FeatureVectorSearchResult, SingleFeatureVectorForSearch}
import com.ideal.linked.toposoid.knowledgebase.model.{KnowledgeBaseEdge, KnowledgeBaseNode}
import com.ideal.linked.toposoid.knowledgebase.regist.model.Knowledge
import com.ideal.linked.toposoid.protocol.model.base.{AnalyzedSentenceObject, AnalyzedSentenceObjects, CoveredPropositionEdge, CoveredPropositionNode, KnowledgeBaseSideInfo, MatchedFeatureInfo}
import com.ideal.linked.toposoid.protocol.model.neo4j.{Neo4jRecordMap, Neo4jRecords}
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import com.typesafe.scalalogging.LazyLogging

import javax.inject._
import play.api._
import play.api.libs.json.{Json, __}
import play.api.mvc._

import scala.util.{Failure, Success, Try}
case class FeatureVectorSearchInfo(propositionId:String, sentenceId:String, sentenceType:Int, lang:String, similarity:Float)
case class SentenceId2FeatureVectorSearchResult(originalSentenceId:String, featureVectorSearchInfo:FeatureVectorSearchInfo)

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(val controllerComponents: ControllerComponents) extends BaseController with DeductionUnitController with LazyLogging {

  def execute()  = Action(parse.json) { request =>
    try {
      val json = request.body
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(json.toString).as[AnalyzedSentenceObjects]
      val remakeAnalyzedSentenceObjects = remakeInputAnalyzedSentenceObjects(analyzedSentenceObjects)
      if(remakeAnalyzedSentenceObjects.size == 0){
        Ok(Json.toJson(analyzedSentenceObjects)).as(JSON)
      }else{
        val asos = remakeAnalyzedSentenceObjects
        val result: List[AnalyzedSentenceObject] = asos.foldLeft(List.empty[AnalyzedSentenceObject]) {
          (acc, x) => acc :+ analyze(x, acc, "sentence-feature-match")
        }
        //TODO:数値チェックどうるるかポリシー決め　同一なrangeオブジェクトがある場合に限るとか。
        Ok(Json.toJson(AnalyzedSentenceObjects(result))).as(JSON)
        //Ok(Json.toJson(getFinalAnalyzedSentenceObjects(analyzedSentenceObjects, deducedAnalyzedSentenceObjects, featureVectorSearchInfoList))).as(JSON)
      }
    }catch {
      case e: Exception => {
        logger.error(e.toString, e)
        BadRequest(Json.obj("status" -> "Error", "message" -> e.toString()))
      }
    }
  }

  /**
   *
   * @param sentenceMap
   * @return
   */
  private def sentence2PropositionId(sentenceMap:List[Map[Int, SentenceInfo]]): List[SentenceId2FeatureVectorSearchResult] = {

    sentenceMap.map(_.map(x => {
      val originalSentenceType = x._1
      val originalSentenceId = x._2.sentenceId
      val vector = FeatureVectorizer.getSentenceVector(Knowledge(x._2.sentence, x._2.lang, "{}"))
      val json: String = Json.toJson(SingleFeatureVectorForSearch(vector = vector.vector, num = conf.getString("TOPOSOID_SENTENCE_VECTORDB_SEARCH_NUM_MAX").toInt)).toString()
      val featureVectorSearchResultJson: String = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "search")
      val result = Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]

      //Claimとして存在している場合に推論が可能になる
      val (ids, similarities) = (result.ids zip result.similarities).foldLeft((List.empty[FeatureVectorIdentifier], List.empty[Float])){
        (acc, x) => {
          x._1.sentenceType match {
            case CLAIM.index => (acc._1 :+ x._1, acc._2 :+ x._2)
            case _ => acc
          }
        }
      }
      val filteredResult = FeatureVectorSearchResult(ids, similarities, result.statusInfo)

      filteredResult.ids.size match {
        case 0 => SentenceId2FeatureVectorSearchResult(originalSentenceId, FeatureVectorSearchInfo(propositionId = "", sentenceId = "", sentenceType = -1, lang = "", similarity = 0))
        case _ => {
          //sentenceごとに最も類似度が高いものを抽出する
          val featureVectorSearchInfoList = extractExistInNeo4JResult(filteredResult, originalSentenceType)
          val featureVectorSearchSelectInfo =  featureVectorSearchInfoList.size match {
            case 0 => FeatureVectorSearchInfo(propositionId = "", sentenceId = "", sentenceType = -1, lang = "", similarity = 0)
            case _ => featureVectorSearchInfoList.maxBy(_.similarity)
          }
          SentenceId2FeatureVectorSearchResult(originalSentenceId, featureVectorSearchSelectInfo)
        }
      }
    })).flatten
  }

  /**
   *
   * @param featureVectorSearchResult
   * @param sentenceType
   * @return
   */
  private def extractExistInNeo4JResult(featureVectorSearchResult:FeatureVectorSearchResult, sentenceType:Int):List[FeatureVectorSearchInfo] = {

    (featureVectorSearchResult.ids zip featureVectorSearchResult.similarities).foldLeft(List.empty[FeatureVectorSearchInfo]){
      (acc, x) => {
        val idInfo = x._1
        val propositionId = idInfo.propositionId
        val lang = idInfo.lang
        val sentenceId = idInfo.featureId
        val similarity = x._2
        //val nodeType: String = ToposoidUtils.getNodeType(sentenceType)
        val query = "MATCH (n) WHERE n.propositionId='%s' AND n.sentenceId='%s' RETURN n".format(propositionId, sentenceId)
        val jsonStr: String = getCypherQueryResult(query, "")
        val neo4jRecords: Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]
        neo4jRecords.records.size match {
          case 0 => acc
          case _ => acc :+ FeatureVectorSearchInfo(propositionId, sentenceId, sentenceType, lang, similarity)
        }
      }
    }

  }

  /**
   *
   * @param analyzedSentenceObjects
   * @return
   */
  private def remakeInputAnalyzedSentenceObjects(analyzedSentenceObjects:AnalyzedSentenceObjects):List[AnalyzedSentenceObject]= {
    //sentenceIdとValdで得られるpropositionIdの対応表を作成する必要がある。
    //val sentenceMap:List[Map[Int, SentenceInfo]] =  analyzedSentenceObjects.analyzedSentenceObjects.map(makeSentence(_))

    val sentenceMap:List[Map[Int, SentenceInfo]] = analyzedSentenceObjects.analyzedSentenceObjects.foldLeft(List.empty[Map[Int, SentenceInfo]]){
      (acc, x) => {
        acc :+ Map(x.knowledgeBaseSemiGlobalNode.sentenceType ->
          SentenceInfo(sentence = x.knowledgeBaseSemiGlobalNode.sentence,
            lang = x.knowledgeBaseSemiGlobalNode.localContextForFeature.lang,
            sentenceId = x.knowledgeBaseSemiGlobalNode.sentenceId,
            propositionId = x.knowledgeBaseSemiGlobalNode.propositionId
          ))
      }
    }

    //ベクトルデータベースに問い合わせ
    val sentenceId2FeatureVectorSearchResult:List[SentenceId2FeatureVectorSearchResult] = sentence2PropositionId(sentenceMap)

    //単純にemptyなところは、前のInputのAnalysisSentenceObjectにして、emptyじゃないところはAnalysisSentenceObjectを置換する。
    analyzedSentenceObjects.analyzedSentenceObjects.map(x => {
      val candidate = sentenceId2FeatureVectorSearchResult.filter(y => y.originalSentenceId == x.knowledgeBaseSemiGlobalNode.sentenceId)
      candidate.head.featureVectorSearchInfo.propositionId match {
        case "" => x
        case _ =>  {
          //類似した文章が、Neo4JにClaimとして存在している場合に推論が可能になる
          val propositionId = candidate.head.featureVectorSearchInfo.propositionId
          val sentenceId = candidate.head.featureVectorSearchInfo.sentenceId
          val sentenceType = candidate.head.featureVectorSearchInfo.sentenceType
          val lang = candidate.head.featureVectorSearchInfo.lang
          val aso = getAnalyzedSentenceObjectBySentenceId(propositionId, sentenceId, sentenceType, lang)
          aso.nodeMap.size match {
            case 0 => x
            case _ => aso
          }
        }
      }
    })
  }


  /**
   * his function is a sub-function of analyze
   * @param edge
   * @param aso
   * @param accParent
   * @return
   */
  def analyzeGraphKnowledge(edge: KnowledgeBaseEdge, aso: AnalyzedSentenceObject, accParent: List[(KnowledgeBaseSideInfo, CoveredPropositionEdge)]): List[(KnowledgeBaseSideInfo, CoveredPropositionEdge)] = {

    val nodeMap: Map[String, KnowledgeBaseNode] = aso.nodeMap
    val sourceKey = edge.sourceId
    val targetKey = edge.destinationId
    val sourceNodeSurface = nodeMap.get(sourceKey).getOrElse().asInstanceOf[KnowledgeBaseNode].predicateArgumentStructure.surface
    val destinationNodeSurface = nodeMap.get(targetKey).getOrElse().asInstanceOf[KnowledgeBaseNode].predicateArgumentStructure.surface

    val nodeType: String = ToposoidUtils.getNodeType(CLAIM.index, LOCAL.index, PREDICATE_ARGUMENT.index)
    val query = "MATCH (n1:%s)-[e]-(n2:%s) WHERE n1.surface='%s' AND e.caseName='%s' AND n2.surface='%s' RETURN n1, e, n2".format(nodeType, nodeType, sourceNodeSurface, edge.caseStr, destinationNodeSurface)
    logger.info(query)
    val jsonStr: String = getCypherQueryResult(query, "")
    //If there is even one that does not match, it is useless to search further
    if (jsonStr.equals("""{"records":[]}""")) return accParent
    val neo4jRecords: Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]


    accParent
    neo4jRecords.records.foldLeft(accParent) {
      (acc, x) => {
        val sourceNode = CoveredPropositionNode(terminalId = sourceKey, terminalSurface = sourceNodeSurface, terminalUrl = "")
        val destinationNode = CoveredPropositionNode(terminalId = targetKey, terminalSurface = destinationNodeSurface, terminalUrl = "")
        val knowledgeBaseSideInfo = KnowledgeBaseSideInfo(propositionId = x.head.value.localNode.get.propositionId, sentenceId = x.head.value.localNode.get.sentenceId, featureInfoList = List.empty[MatchedFeatureInfo])
        val coveredPropositionEdge = CoveredPropositionEdge(sourceNode = sourceNode, destinationNode = destinationNode)
        acc :+ (knowledgeBaseSideInfo, coveredPropositionEdge)
      }
    }

  }
  /*
  def analyzeGraphKnowledge(edge: KnowledgeBaseEdge,aso:AnalyzedSentenceObject, accParent: (List[List[Neo4jRecordMap]], List[MatchedPropositionInfo], List[CoveredPropositionEdge])): (List[List[Neo4jRecordMap]], List[MatchedPropositionInfo], List[CoveredPropositionEdge]) = {

    val nodeMap: Map[String, KnowledgeBaseNode] = aso.nodeMap

    val sourceKey = edge.sourceId
    val targetKey = edge.destinationId
    val sourceNodeSurface = nodeMap.get(sourceKey).getOrElse().asInstanceOf[KnowledgeBaseNode].predicateArgumentStructure.surface
    val destinationNodeSurface = nodeMap.get(targetKey).getOrElse().asInstanceOf[KnowledgeBaseNode].predicateArgumentStructure.surface

    val nodeType: String = ToposoidUtils.getNodeType(CLAIM.index, LOCAL.index, PREDICATE_ARGUMENT.index)
    val query = "MATCH (n1:%s)-[e]-(n2:%s) WHERE n1.surface='%s' AND e.caseName='%s' AND n2.surface='%s' RETURN n1, e, n2".format(nodeType, nodeType, sourceNodeSurface, edge.caseStr, destinationNodeSurface)
    logger.info(query)
    val jsonStr: String = getCypherQueryResult(query, "")
    //If there is even one that does not match, it is useless to search further
    if (jsonStr.equals("""{"records":[]}""")) return accParent
    val neo4jRecords: Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]
    neo4jRecords.records.foldLeft(accParent) {
      (acc, x) => {
        val neo4jRecordMap = acc._1 :+ x
        val matchedPropositionInfoList = acc._2 :+ MatchedPropositionInfo(x.head.value.localNode.get.propositionId, List(MatchedFeatureInfo(x.head.value.localNode.get.sentenceId, 1)))
        val sourceNode = CoveredPropositionNode(terminalId = sourceKey, terminalSurface = sourceNodeSurface, terminalUrl = "")
        val destinationNode = CoveredPropositionNode(terminalId = targetKey, terminalSurface = destinationNodeSurface, terminalUrl = "")
        val coveredPropositionEdgeList = acc._3 :+ CoveredPropositionEdge(sourceNode = sourceNode, destinationNode = destinationNode)
        (neo4jRecordMap, matchedPropositionInfoList, coveredPropositionEdgeList)
      }
    }
  }
*/


}