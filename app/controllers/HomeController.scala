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
import com.ideal.linked.toposoid.common.{CLAIM, LOCAL, PREDICATE_ARGUMENT, TRANSVERSAL_STATE, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.deduction.common.FacadeForAccessNeo4J.{getAnalyzedSentenceObjectBySentenceId, getCypherQueryResult}
import com.ideal.linked.toposoid.deduction.common.FacadeForAccessVectorDB.getMatchedSentenceFeature
import com.ideal.linked.toposoid.deduction.common.{DeductionUnitController, DeductionUnitControllerForSemiGlobal, SentenceInfo}
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
//case class FeatureVectorSearchInfo(propositionId:String, sentenceId:String, sentenceType:Int, lang:String, similarity:Float)
//case class SentenceId2FeatureVectorSearchResult(originalSentenceId:String, featureVectorSearchInfo:FeatureVectorSearchInfo)

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(val controllerComponents: ControllerComponents) extends BaseController with DeductionUnitControllerForSemiGlobal with LazyLogging {

  def execute()  = Action(parse.json) { request =>
    val transversalState = Json.parse(request.headers.get(TRANSVERSAL_STATE .str).get).as[TransversalState]
    try {
      val json = request.body
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(json.toString).as[AnalyzedSentenceObjects]
      val asos: List[AnalyzedSentenceObject] = analyzedSentenceObjects.analyzedSentenceObjects
      val result: List[AnalyzedSentenceObject] = asos.foldLeft(List.empty[AnalyzedSentenceObject]) {
        (acc, x) => acc :+ analyze(x, acc, "sentence-feature-match", List.empty[Int], transversalState)
      }
      logger.info(ToposoidUtils.formatMessageForLogger("deduction completed.", transversalState.username))
      Ok(Json.toJson(AnalyzedSentenceObjects(result))).as(JSON)
    }catch {
      case e: Exception => {
        logger.error(ToposoidUtils.formatMessageForLogger(e.toString, transversalState.username), e)
        BadRequest(Json.obj("status" -> "Error", "message" -> e.toString()))
      }
    }
  }

  def analyzeGraphKnowledgeForSemiGlobal(aso: AnalyzedSentenceObject, transversalState:TransversalState): List[KnowledgeBaseSideInfo] = {
    getMatchedSentenceFeature(aso.knowledgeBaseSemiGlobalNode.sentenceId,
      aso.knowledgeBaseSemiGlobalNode.sentenceType,
      aso.knowledgeBaseSemiGlobalNode.sentence,
      aso.knowledgeBaseSemiGlobalNode.localContextForFeature.lang,
      transversalState)
  }

}