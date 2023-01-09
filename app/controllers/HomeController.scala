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
import com.ideal.linked.toposoid.common.{CLAIM, PREMISE, ToposoidUtils}
import com.ideal.linked.toposoid.deduction.common.AnalyzedSentenceObjectUtils.makeSentence
import com.ideal.linked.toposoid.deduction.common.FacadeForAccessNeo4J.{existALlPropositionIdEqualId, getCypherQueryResult, havePremiseNode, neo4JData2AnalyzedSentenceObjectByPropositionId}
import com.ideal.linked.toposoid.deduction.common.{SentenceInfo}
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.{FeatureVectorSearchResult, SingleFeatureVectorForSearch}
import com.ideal.linked.toposoid.knowledgebase.model.{KnowledgeBaseEdge, KnowledgeBaseNode}
import com.ideal.linked.toposoid.knowledgebase.regist.model.Knowledge
import com.ideal.linked.toposoid.protocol.model.base.{AnalyzedSentenceObject, AnalyzedSentenceObjects, DeductionResult}
import com.ideal.linked.toposoid.protocol.model.neo4j.{Neo4jRecordMap, Neo4jRecords}
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import com.typesafe.scalalogging.LazyLogging

import javax.inject._
import play.api._
import play.api.libs.json.Json
import play.api.mvc._

import scala.util.{Failure, Success, Try}
case class FeatureVectorSearchInfo(propositionId:String, sentenceId:String, sentenceType:Int, lang:String)
case class SentenceId2FeatureVectorSearchResult(originalSentenceId:String, featureVectorSearchInfoList:List[FeatureVectorSearchInfo])

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(val controllerComponents: ControllerComponents) extends BaseController with LazyLogging{

  def execute()  = Action(parse.json) { request =>
    try {
      val json = request.body
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(json.toString).as[AnalyzedSentenceObjects]
      val remakeAnalyzedSentenceObjectsInfo = remakeInputAnalyzedSentenceObjects(analyzedSentenceObjects)
      val remakeAnalyzedSentenceObjects:List[AnalyzedSentenceObjects] = remakeAnalyzedSentenceObjectsInfo._1
      val featureVectorSearchInfoList:List[SentenceId2FeatureVectorSearchResult] = remakeAnalyzedSentenceObjectsInfo._2
      if(remakeAnalyzedSentenceObjects.size == 0){
        logger.info("check-----------------------------------------------------------------")
        Ok(Json.toJson(analyzedSentenceObjects)).as(JSON)
      }else{
        val deducedAnalyzedSentenceObjects:List[AnalyzedSentenceObjects] = remakeAnalyzedSentenceObjects.map( deduction(_))
        //TODO:数値チェックどうるるかポリシー決め　同一なrangeオブジェクトがある場合に限るとか。
        Ok(Json.toJson(getFinalAnalyzedSentenceObjects(analyzedSentenceObjects, deducedAnalyzedSentenceObjects, featureVectorSearchInfoList))).as(JSON)
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
      val originalSentenceId = x._2.sentenceId
      val vector = FeatureVectorizer.getVector(Knowledge(x._2.sentence, x._2.lang, "{}"))
      val json:String = Json.toJson(SingleFeatureVectorForSearch(vector=vector.vector, num=conf.getString("TOPOSOID_VALD_SEARCH_NUM_MAX").toInt, radius=(-1.0f), epsilon=0.01f, timeout=50000000000L)).toString()
      val featureVectorSearchResultJson:String = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_VALD_ACCESSOR_HOST"), "9010", "search")
      val result = Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]
      logger.info("-----------------------------------------------------------------")
      logger.info(result.ids.toString())
      logger.info("-----------------------------------------------------------------")
      result.ids.size match {
        case 0 => SentenceId2FeatureVectorSearchResult(originalSentenceId, List.empty[FeatureVectorSearchInfo])
        case _ => SentenceId2FeatureVectorSearchResult(originalSentenceId, result.ids.map(y => {
          val idInfo = y.split("#")
          val propositionId = idInfo(0)
          val lang = idInfo(1)
          val sentenceId = idInfo(2)
          FeatureVectorSearchInfo(propositionId, sentenceId, x._1, lang)
        }))
      }
    })).flatten
  }


  /**
   *
   * @param analyzedSentenceObjects
   * @param propositionIdsMap
   * @return
   */
  private def extractFeatureVectorSearchInfoList(analyzedSentenceObjects:List[AnalyzedSentenceObjects], featureVectorSearchInfoList:List[SentenceId2FeatureVectorSearchResult]):List[SentenceId2FeatureVectorSearchResult] = {
    val selectedPropositionIdSet = analyzedSentenceObjects.map(_.analyzedSentenceObjects.foldLeft(List.empty[String]){
      (acc, y) => { acc :+  y.nodeMap.head._2.propositionId }
    }).flatten.toSet
    featureVectorSearchInfoList.filter(_.featureVectorSearchInfoList.filter(selectedPropositionIdSet  contains _.propositionId).size > 0)
  }

  /**
   *
   * @param analyzedSentenceObjects
   * @return
   */
  private def remakeInputAnalyzedSentenceObjects(analyzedSentenceObjects:AnalyzedSentenceObjects):(List[AnalyzedSentenceObjects], List[SentenceId2FeatureVectorSearchResult]) = {
    //sentenceId(nodeIdのprefix)とValdで得られるpropositionIdの対応表を作成する必要がある。
    val sentenceMap:List[Map[Int, SentenceInfo]] =  analyzedSentenceObjects.analyzedSentenceObjects.map(makeSentence(_))
    val sentenceId2FeatureVectorSearchResult:List[SentenceId2FeatureVectorSearchResult] = sentence2PropositionId(sentenceMap)

    val premiseCount = analyzedSentenceObjects.analyzedSentenceObjects.filter(_.sentenceType == PREMISE.index).size
    val claimCount = analyzedSentenceObjects.analyzedSentenceObjects.filter(_.sentenceType == CLAIM.index).size
    val havePremise:Boolean =  premiseCount > 0
    val haveClaim:Boolean =  claimCount > 0

    //ここは、命題に対して完全に満たされていなくても良いという立場を撮っている。それは部分的には正しいという回答をする仕様になっているためだ。
    //ただし、附則している情報は補う必要がある。
    val existPremiseInNeo4j:Boolean = sentenceId2FeatureVectorSearchResult.filter(_.featureVectorSearchInfoList.filter(_.sentenceType == PREMISE.index).size > 0).size > 0 && premiseCount > 0
    val existClaimInNeo4j:Boolean = sentenceId2FeatureVectorSearchResult.filter(_.featureVectorSearchInfoList.filter(_.sentenceType == CLAIM.index).size > 0).size > 0 && claimCount > 0

    if(havePremise && haveClaim && existPremiseInNeo4j && existClaimInNeo4j) {
      //premiseとclaimがつながっていることを確認。→　それぞれ共通のpropositionIdを持っていることを確認
      val premiseIdsSet: Set[String] = sentenceId2FeatureVectorSearchResult.filter(_.featureVectorSearchInfoList.filter(_.sentenceType == PREMISE.index).size > 0).map(_.featureVectorSearchInfoList.map(_.propositionId)).flatten.toSet
      val claimIdsSet: Set[String] = sentenceId2FeatureVectorSearchResult.filter(_.featureVectorSearchInfoList.filter(_.sentenceType == CLAIM.index).size > 0).map(_.featureVectorSearchInfoList.map(_.propositionId)).flatten.toSet
      //有効そうなPropositionIdからAnalyzedSentenceObjectsを作る。
      val targetPropositionIds = premiseIdsSet & claimIdsSet

      val asos = targetPropositionIds.foldLeft(List.empty[AnalyzedSentenceObjects]) { (acc, x) => {
        val concatAsos:List[AnalyzedSentenceObject] = neo4JData2AnalyzedSentenceObjectByPropositionId(x, PREMISE.index).analyzedSentenceObjects ::: neo4JData2AnalyzedSentenceObjectByPropositionId(x, CLAIM.index).analyzedSentenceObjects
        acc :+ AnalyzedSentenceObjects(concatAsos)
      }}
      //valdとneo4jで同期が取れていない場合があるのでケア
      val validAsos = asos.filter(_.analyzedSentenceObjects.filter(_.nodeMap.size > 0).size > 0)
      validAsos.size match {
        case 0 => (List.empty[AnalyzedSentenceObjects], List.empty[SentenceId2FeatureVectorSearchResult])
        case _ => (validAsos, extractFeatureVectorSearchInfoList(validAsos, sentenceId2FeatureVectorSearchResult))
      }
    }else if(haveClaim && existClaimInNeo4j){
      val claimIdsSet:Set[String] = sentenceId2FeatureVectorSearchResult.filter(_.featureVectorSearchInfoList.filter(_.sentenceType == CLAIM.index).size > 0).map(_.featureVectorSearchInfoList.map(_.propositionId)).flatten.toSet
      //valdとneo4jで同期が取れていない場合があるのでケア
      val asos = claimIdsSet.foldLeft(List.empty[AnalyzedSentenceObjects]) {(acc, x) => {
        acc :+ neo4JData2AnalyzedSentenceObjectByPropositionId(x, CLAIM.index)
      }}
      val validAsos = asos.filter(_.analyzedSentenceObjects.filter(_.nodeMap.size > 0).size > 0)
      validAsos.size match {
        case 0 => (List.empty[AnalyzedSentenceObjects], List.empty[SentenceId2FeatureVectorSearchResult])
        case _ =>  (validAsos, extractFeatureVectorSearchInfoList(validAsos, sentenceId2FeatureVectorSearchResult))
      }
    }else if(havePremise && existPremiseInNeo4j){
      val premiseIdsSet:Set[String] = sentenceId2FeatureVectorSearchResult.filter(_.featureVectorSearchInfoList.filter(_.sentenceType == PREMISE.index).size > 0).map(_.featureVectorSearchInfoList.map(_.propositionId)).flatten.toSet
      //claimがないという命題はないので、与えられた命題claimをセットする。
      val claimList:List[AnalyzedSentenceObject] = analyzedSentenceObjects.analyzedSentenceObjects.filter(_.sentenceType == CLAIM.index)
      //valdとneo4jで同期が取れていない場合があるのでケア
      //TODO:下記要確認
      //-----------------------------------------------------------------------------------------------
      val asos = premiseIdsSet.foldLeft(List.empty[AnalyzedSentenceObjects]) {(acc, x) => {
        //与えられた命題のPremiseがNeo4JにClaimとして存在している場合に推論が可能になる
        val claimAos = neo4JData2AnalyzedSentenceObjectByPropositionId(x, CLAIM.index)
        //しかし、claimとしてではなくpremise推論したいのではないので、sentenceTypeをpremiseに変更
        claimAos.analyzedSentenceObjects.foldLeft(acc){
          (acc2, y) => {
            acc2 :+ AnalyzedSentenceObjects(List(AnalyzedSentenceObject(y.nodeMap, y.edgeList, PREMISE.index, y.sentenceId, y.lang, y.deductionResultMap)) ::: claimList)
          }
        }
      }}
      //-----------------------------------------------------------------------------------------------
      val validAsos = asos.filter(_.analyzedSentenceObjects.filter(_.nodeMap.size > 0).size > 0)
      validAsos.size match {
        case 0 => (List.empty[AnalyzedSentenceObjects], List.empty[SentenceId2FeatureVectorSearchResult])
        case _ =>  (validAsos, extractFeatureVectorSearchInfoList(validAsos, sentenceId2FeatureVectorSearchResult))
      }
    }else{
      (List.empty[AnalyzedSentenceObjects], List.empty[SentenceId2FeatureVectorSearchResult])
    }
  }

  /**
   *
   * @param analyzedSentenceObjects
   * @return
   */
  private def deduction(analyzedSentenceObjects:AnalyzedSentenceObjects):AnalyzedSentenceObjects={
    AnalyzedSentenceObjects(analyzedSentenceObjects.analyzedSentenceObjects.map(analyze(_)))
  }


  /**
   *
   * @param originalAnalyzedSentenceObjects
   * @param analyzedSentenceObjectsList
   * @return
   */
  private def getFinalAnalyzedSentenceObjects(originalAnalyzedSentenceObjects:AnalyzedSentenceObjects,
                                              analyzedSentenceObjectsList:List[AnalyzedSentenceObjects],
                                              featureVectorSearchInfoList:List[SentenceId2FeatureVectorSearchResult]):AnalyzedSentenceObjects = {

    val correctAnalyzedSentenceObjects = analyzedSentenceObjectsList.filter(_.analyzedSentenceObjects.filter(_.deductionResultMap.filter(_._2.status).size > 0).size >0)
    //val deducedPropositionIdSet:Set[String] = correctAnalyzedSentenceObjects.map(_.analyzedSentenceObjects.map(_.nodeMap.head._2.propositionId)).flatten.toSet
    val finalAnalyzedSentenceObjects:List[AnalyzedSentenceObject] = correctAnalyzedSentenceObjects.size match {
      case 0 => originalAnalyzedSentenceObjects.analyzedSentenceObjects
      case _ => {
        originalAnalyzedSentenceObjects.analyzedSentenceObjects.map(x => {
          //与えられた命題をセンテンスごとに見て行く。センテンスにはIDが付けられるので、それを取得
          //与えられたsentenceIdのセンテンスと類似した文章がNeo4jに登録されえている場合、featureVectorSearchInfoListに存在しているはずなのでそれのsentenceIdを取得する。
          val candidateSentenceIds:Set[(String)] = featureVectorSearchInfoList.filter(_.originalSentenceId == x.sentenceId).size > 0 match {
            case true => featureVectorSearchInfoList.filter(_.originalSentenceId == x.sentenceId).map(_.featureVectorSearchInfoList.map(_.sentenceId)).flatten.toSet
            case _ => Set.empty[String]
          }

          //上記のpropositionIdとsentenceIdを持っているcorrectAnalyzedSentenceObjectsが対象とるなる。
          val deductionResultMaps:List[Map[String,DeductionResult]]=  correctAnalyzedSentenceObjects.map(_.analyzedSentenceObjects.filter(candidateSentenceIds contains _.sentenceId).map(_.deductionResultMap)).flatten
          val targetDeductionResultMaps = deductionResultMaps.filter(_.get(x.sentenceType.toString).get.status)

          targetDeductionResultMaps.size > 0 match {
            case true => AnalyzedSentenceObject(x.nodeMap, x.edgeList, x.sentenceType, x.sentenceId, x.lang, getDeductionResultMap(x.sentenceType, targetDeductionResultMaps))
            case _ => x
          }
        })
      }
    }
    AnalyzedSentenceObjects(finalAnalyzedSentenceObjects)
  }

  private def getDeductionResultMap(sentenceType:Int,  correctDeductionResultMapList:List[Map[String,DeductionResult]]):Map[String,DeductionResult] = {
    val emptySideDeductionResultMap:Map[String,DeductionResult] = sentenceType match{
      case 0 => Map("1" -> DeductionResult(false,  List.empty[String], ""))
      case 1 => Map("0" -> DeductionResult(false,  List.empty[String], ""))
      case _ => Map.empty[String,DeductionResult]
    }

    val effectiveDeductionResultMapList:List[Map[String,DeductionResult]] =  correctDeductionResultMapList.filter(_.get(sentenceType.toString).get.status)
    effectiveDeductionResultMapList.size match {
      case 0 =>Map(sentenceType.toString -> DeductionResult(false,  List.empty[String], "")) ++ emptySideDeductionResultMap
      case _ => {
        effectiveDeductionResultMapList.foldLeft(Map.empty[String,DeductionResult]){
          (acc, x) => {
            acc.size match {
              case 0 =>  x ++ emptySideDeductionResultMap
              case _ => {
                val deductionResultMapAcc = acc.get(sentenceType.toString).get
                val deductionResultMapY = x.get(sentenceType.toString).get
                val propositionIds = deductionResultMapAcc.matchedPropositionIds ::: deductionResultMapY.matchedPropositionIds
                Map(sentenceType.toString -> DeductionResult(true,  propositionIds, "sentence-vector-match")) ++ emptySideDeductionResultMap
              }
            }
          }
        }
      }
    }
  }



  /**
   * This function analyzes whether the entered text exactly matches.
   * @param aso
   * @return
   */
  private def analyze(aso:AnalyzedSentenceObject): AnalyzedSentenceObject ={

    val (searchResults, propositionIds) = aso.edgeList.foldLeft((List.empty[List[Neo4jRecordMap]], List.empty[String])){
      (acc, x) => analyzeGraphKnowledge(x, aso.nodeMap, aso.sentenceType, acc)
    }
    if(propositionIds.size == 0) return aso
    if(aso.sentenceType == 0){
      //f the proposition is premise, check only if the same proposition exists as claim
      checkFinal(propositionIds, aso, searchResults)
    }else if(aso.sentenceType == 1){
      //If the proposition is a claim, check whether the proposition holds only as a claim or through premise.
      val onlyClaimPropositionIds = propositionIds.filterNot(havePremiseNode(_))
      if (onlyClaimPropositionIds.size > 0){
        //A case where a proposition (claim) can be true only by claim in the knowledge base
        checkFinal(onlyClaimPropositionIds, aso, searchResults)
      }else{
        //The case where the proposition (claim) becomes true via premis in knowledge base
        val claimHavingPremisePropositionIds = propositionIds.filter(havePremiseNode(_))
        val checkedPremiseAso =  checkClaimHavingPremise(claimHavingPremisePropositionIds.distinct, aso)
        if(checkedPremiseAso.deductionResultMap.get(aso.sentenceType.toString).get.matchedPropositionIds.size > 0){
          checkFinal(claimHavingPremisePropositionIds, checkedPremiseAso, searchResults)
        }else{
          aso
        }
      }
    }else{
      aso
    }
  }

  /**
   * A function that checks whether a proposition holds a claim via premise
   * @param targetPropositionIds
   * @param aso
   * @return
   */
  private def checkClaimHavingPremise(targetPropositionIds:List[String], aso:AnalyzedSentenceObject):AnalyzedSentenceObject ={
    for(propositionId <- targetPropositionIds){
      val updateAso = checkClaimHavingPremiseImpl(propositionId, aso)
      if(updateAso.deductionResultMap.get(aso.sentenceType.toString).get.matchedPropositionIds.size > 0) return updateAso
    }
    aso
  }

  /**
   * Concrete implementation of checkClaimHavingPremise
   * @param propositionId
   * @param aso
   * @return
   */
  private def checkClaimHavingPremiseImpl(targetPropositionId:String, aso:AnalyzedSentenceObject): AnalyzedSentenceObject = {
    //Pick up a node with the same surface layer as the Premise connected from Claim as x
    val query = "MATCH (n:PremiseNode)-[*]-(m:ClaimNode), (x:ClaimNode) WHERE m.propositionId ='%s' AND x.surface=n.surface  RETURN (n), (x)".format(targetPropositionId)
    val jsonStr = getCypherQueryResult(query, "x")
    val neo4jRecords:Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]

    if(neo4jRecords.records.size > 0){
      val targetPropositionId1Set = neo4jRecords.records.map(_.filter(_.key.equals("x")).map(_.value.logicNode.propositionId)).flatten.toSet
      val targetAnalyzedSentenceObjectsFromNeo4j:List[AnalyzedSentenceObject] = neo4JData2AnalyzedSentenceObjectByPropositionId(targetPropositionId, 0).analyzedSentenceObjects

      val checkedAso:Set[AnalyzedSentenceObject] = targetPropositionId1Set.map(targetPropositionId1 =>{
        val sentenceInfo1 = makeSentence(neo4JData2AnalyzedSentenceObjectByPropositionId(targetPropositionId1, 1).analyzedSentenceObjects.head)
        //Acquired information x from Neo4j contains multiple pieces of text information (for example, partially matching items, etc.), and it is necessary to compare each of them.
        targetAnalyzedSentenceObjectsFromNeo4j.foldLeft(aso){
          (acc, x) => {
            val sentenceInfo2 = makeSentence(x)
            if (sentenceInfo1.get(1).get.sentence.equals(sentenceInfo2.get(0).get.sentence)) {
              val coveredPropositionIds = List(sentenceInfo1.get(1).get.propositionId, sentenceInfo2.get(0).get.propositionId)
              //Here, only the proposalId is added without outputting the final result. Leave the final decision to the checkFinal function
              val deductionResult: DeductionResult = new DeductionResult(false, aso.deductionResultMap.get(aso.sentenceType.toString).get.matchedPropositionIds ::: coveredPropositionIds, "")
              val updateDeductionResultMap = aso.deductionResultMap.updated(aso.sentenceType.toString, deductionResult)
              AnalyzedSentenceObject(aso.nodeMap, aso.edgeList, aso.sentenceType, aso.sentenceId, aso.lang, updateDeductionResultMap)
            }else{
              acc
            }
          }}
      })
      //If there are multiple premises, all corresponding Claims are required
      if(checkedAso.filter(_.deductionResultMap(aso.sentenceType.toString).matchedPropositionIds.size > 0).size == targetAnalyzedSentenceObjectsFromNeo4j.size){
        checkedAso.filter(_.deductionResultMap(aso.sentenceType.toString).matchedPropositionIds.size > 0).head
      }else{
        aso
      }

    }else{
      aso
    }
  }

  /**
   *　final check
   * @param targetPropositionIds
   * @param aso
   * @param searchResults
   * @return
   */
  private def checkFinal(targetPropositionIds:List[String], aso:AnalyzedSentenceObject, searchResults:List[List[Neo4jRecordMap]]): AnalyzedSentenceObject ={
    if(targetPropositionIds.size < aso.edgeList.size) return aso
    //Pick up the most frequent propositionId
    val maxFreqSize = targetPropositionIds.groupBy(identity).mapValues(_.size).maxBy(_._2)._2
    val propositionIdsHavingMaxFreq:List[String] = targetPropositionIds.groupBy(identity).mapValues(_.size).filter(_._2 == maxFreqSize).map(_._1).toList
    logger.debug(propositionIdsHavingMaxFreq.toString())
    //It is assumed that it is sufficient if the object can be covered by nodes and edges.
    val coveredPropositionIds =  propositionIdsHavingMaxFreq.filter(x => searchResults.filter(y =>  existALlPropositionIdEqualId(x, y)).size >=  aso.edgeList.size)
    if(coveredPropositionIds.size == 0) return aso
    val status = true
    //selectedPropositions includes trivialClaimsPropositionIds
    val additionalPropositionIds = aso.deductionResultMap.get(aso.sentenceType.toString).get.matchedPropositionIds
    val deductionResult:DeductionResult = new DeductionResult(status, coveredPropositionIds:::additionalPropositionIds, "")
    val updateDeductionResultMap = aso.deductionResultMap.updated(aso.sentenceType.toString, deductionResult)
    AnalyzedSentenceObject(aso.nodeMap, aso.edgeList, aso.sentenceType, aso.sentenceId, aso.lang, updateDeductionResultMap)

  }

  /**
   * This function is a sub-function of analyze
   * @param nodeMap
   * @param sentenceType
   * @param accParent
   * @return
   */
  private def analyzeGraphKnowledge(edge:KnowledgeBaseEdge, nodeMap:Map[String, KnowledgeBaseNode], sentenceType:Int, accParent:(List[List[Neo4jRecordMap]], List[String])): (List[List[Neo4jRecordMap]], List[String]) = {

    val sourceKey = edge.sourceId
    val targetKey = edge.destinationId
    val sourceNodeSurface = nodeMap.get(sourceKey).getOrElse().asInstanceOf[KnowledgeBaseNode].surface
    val destinationNodeSurface = nodeMap.get(targetKey).getOrElse().asInstanceOf[KnowledgeBaseNode].surface
    val nodeType:String = ToposoidUtils.getNodeType(sentenceType)

    val initAcc = sentenceType match{
      case PREMISE.index => {
        val nodeType:String = ToposoidUtils.getNodeType(CLAIM.index)
        val query = "MATCH (n1:%s)-[e]-(n2:%s) WHERE n1.surface='%s' AND e.caseName='%s' AND n2.surface='%s' RETURN n1, e, n2".format(nodeType, nodeType, sourceNodeSurface, edge.caseStr, destinationNodeSurface)
        logger.info(query)
        val jsonStr:String = getCypherQueryResult(query, "")
        //If there is even one that does not match, it is useless to search further
        if(jsonStr.equals("""{"records":[]}""")) return (List.empty[List[Neo4jRecordMap]], List.empty[String])
        val neo4jRecords:Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]
        neo4jRecords.records.foldLeft(accParent){
          (acc, x) => { (acc._1 :+ x, acc._2 :+ x.head.value.logicNode.propositionId)}
        }
      }
      case _ => accParent
    }

    val query = "MATCH (n1:%s)-[e]-(n2:%s) WHERE n1.surface='%s' AND e.caseName='%s' AND n2.surface='%s' RETURN n1, e, n2".format(nodeType, nodeType, sourceNodeSurface, edge.caseStr, destinationNodeSurface)
    logger.info(query)
    val jsonStr:String = getCypherQueryResult(query, "")
    //If there is even one that does not match, it is useless to search further
    if(jsonStr.equals("""{"records":[]}""")) return initAcc
    val neo4jRecords:Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]
    neo4jRecords.records.foldLeft(initAcc){
      (acc, x) => { (acc._1 :+ x, acc._2 :+ x.head.value.logicNode.propositionId)}
    }
  }

}