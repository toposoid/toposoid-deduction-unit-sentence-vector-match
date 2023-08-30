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

import akka.util.Timeout
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.data.accessor.neo4j.Neo4JAccessor
import com.ideal.linked.toposoid.common.ToposoidUtils
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.FeatureVectorId
import com.ideal.linked.toposoid.knowledgebase.regist.model.{Knowledge, KnowledgeSentenceSet, PropositionRelation}
import com.ideal.linked.toposoid.protocol.model.base.AnalyzedSentenceObjects
import com.ideal.linked.toposoid.protocol.model.parser.{InputSentenceForParser, KnowledgeForParser, KnowledgeSentenceSetForParser}
import com.ideal.linked.toposoid.sentence.transformer.neo4j.Sentence2Neo4jTransformer
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import io.jvm.uuid.UUID
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Play.materializer
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.test.Helpers.{POST, contentType, status, _}
import play.api.test.{FakeRequest, _}

import scala.concurrent.duration.DurationInt

class HomeControllerSpecJapanese1 extends PlaySpec with BeforeAndAfter with BeforeAndAfterAll with GuiceOneAppPerSuite  with DefaultAwaitTimeout with Injecting {

  before {
    Neo4JAccessor.delete()
    Thread.sleep(5000)
  }

  override def beforeAll(): Unit = {
    Neo4JAccessor.delete()
  }

  override def afterAll(): Unit = {
    Neo4JAccessor.delete()
  }

  override implicit def defaultAwaitTimeout: Timeout = 600.seconds
  val controller: HomeController = inject[HomeController]

  val sentenceA = "自然界の法則がすべての慣性系で同じように成り立っている。"
  val sentenceB = "どの慣性系から見ても光の速さは一定である。"
  val sentenceC = "運動する物体の速さの上限は光の速さである。"

  val paraphraseA = "自然界の物理法則は例外なくどの慣性系でも成立する。"
  val paraphraseB = "見ている慣性系によらず光速は不変である。"
  val paraphraseC = "物体の運動する速さは光の速さを超えない。"


  def registSingleClaim(knowledgeForParser:KnowledgeForParser): Unit = {
    val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
      List.empty[KnowledgeForParser],
      List.empty[PropositionRelation],
      List(knowledgeForParser),
      List.empty[PropositionRelation])
    Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser)
    FeatureVectorizer.createVector(knowledgeSentenceSetForParser)
    Thread.sleep(5000)
  }

  private def deleteFeatureVector(propositionId:String, lang:String, sentenceId:String):Unit = {
    val json: String = Json.toJson(FeatureVectorId(id = propositionId + "#" + lang + "#" + sentenceId )).toString()
    ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_VALD_ACCESSOR_HOST"), "9010", "delete")
  }

  "The specification1" should {
    "returns an appropriate response" in {
      val propositionId1 = UUID.random.toString
      val sentenceId1 = UUID.random.toString
      val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}", false)
      val paraphrase1 = Knowledge(paraphraseA,"ja_JP", "{}", false)
      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1))
      val propositionIdForInference = UUID.random.toString
      val premiseKnowledge = List.empty[KnowledgeForParser]
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase1))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = ToposoidUtils.callComponent(inputSentence, conf.getString("SENTENCE_PARSER_JP_WEB_HOST"), "9001", "analyze")
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("0").get.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.havePremiseInGivenProposition).size == 0)
      deleteFeatureVector(propositionId1, "ja_JP", sentenceId1)
    }
  }
  /*
  "The specification2" should {
    "returns an appropriate response" in {
      val propositionId1 = UUID.random.toString
      val sentenceId1 = UUID.random.toString
      val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}", false)
      val paraphrase1 = Knowledge(paraphraseA,"ja_JP", "{}", false)
      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledge1)),
        List.empty[PropositionRelation],
        List.empty[KnowledgeForParser],
        List.empty[PropositionRelation])
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser)
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser)
      Thread.sleep(5000)
      val propositionIdForInference = UUID.random.toString
      val premiseKnowledge = List.empty[KnowledgeForParser]
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase1))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = ToposoidUtils.callComponent(inputSentence, conf.getString("SENTENCE_PARSER_JP_WEB_HOST"), "9001", "analyze")
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("0").get.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.status).size == 0)
      deleteFeatureVector(propositionId1, "ja_JP", sentenceId1)
    }
  }
  */
  "The specification2" should {
    "returns an appropriate response" in {
      val propositionId1 = UUID.random.toString
      val sentenceId1 = UUID.random.toString
      val sentenceId2 = UUID.random.toString
      val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}")
      val knowledge2 = Knowledge(sentenceB,"ja_JP", "{}")
      val paraphrase1 = Knowledge(paraphraseA,"ja_JP", "{}", false)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledge1)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId1, sentenceId2, knowledge2)),
        List.empty[PropositionRelation])
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser)
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser)
      Thread.sleep(5000)
      val propositionIdForInference = UUID.random.toString
      val premiseKnowledge = List.empty[KnowledgeForParser]
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase1))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = ToposoidUtils.callComponent(inputSentence, conf.getString("SENTENCE_PARSER_JP_WEB_HOST"), "9001", "analyze")
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("0").get.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.havePremiseInGivenProposition).size == 0)
      deleteFeatureVector(propositionId1, "ja_JP", sentenceId1)
      deleteFeatureVector(propositionId1, "ja_JP", sentenceId2)

    }
  }

  "The specification3" should {
    "returns an appropriate response" in {
      val propositionId1 = UUID.random.toString
      val sentenceId1 = UUID.random.toString
      val sentenceId2 = UUID.random.toString
      val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}")
      val knowledge2 = Knowledge(sentenceB,"ja_JP", "{}")
      val paraphrase2 = Knowledge(paraphraseB,"ja_JP", "{}", false)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledge1)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId1, sentenceId2, knowledge2)),
        List.empty[PropositionRelation])
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser)
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser)
      Thread.sleep(5000)

      val propositionIdForInference = UUID.random.toString
      val premiseKnowledge =List.empty[KnowledgeForParser]
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase2))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = ToposoidUtils.callComponent(inputSentence, conf.getString("SENTENCE_PARSER_JP_WEB_HOST"), "9001", "analyze")
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("0").get.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.havePremiseInGivenProposition).size == 0)
      deleteFeatureVector(propositionId1, "ja_JP", sentenceId1)
      deleteFeatureVector(propositionId1, "ja_JP", sentenceId2)

    }
  }

  "The specification4" should {
    "returns an appropriate response" in {
      val propositionId1 = UUID.random.toString
      val propositionId2 = UUID.random.toString
      val sentenceId1 = UUID.random.toString
      val sentenceId2 = UUID.random.toString
      val sentenceId3 = UUID.random.toString
      val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}")
      val knowledge2 = Knowledge(sentenceB,"ja_JP", "{}")
      val paraphrase1 = Knowledge(paraphraseA,"ja_JP", "{}", false)
      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1))

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId2, sentenceId2, knowledge1)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId2, sentenceId3, knowledge2)),
        List.empty[PropositionRelation])
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser)
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser)
      Thread.sleep(5000)

      val propositionIdForInference = UUID.random.toString
      val premiseKnowledge = List.empty[KnowledgeForParser]
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase1))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = ToposoidUtils.callComponent(inputSentence, conf.getString("SENTENCE_PARSER_JP_WEB_HOST"), "9001", "analyze")
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("0").get.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.havePremiseInGivenProposition).size == 0)
      deleteFeatureVector(propositionId1, "ja_JP", sentenceId1)
      deleteFeatureVector(propositionId2, "ja_JP", sentenceId2)
      deleteFeatureVector(propositionId2, "ja_JP", sentenceId3)
    }
  }

  "The specification5" should {
    "returns an appropriate response" in {
      val propositionId1 = UUID.random.toString
      val sentenceId1 = UUID.random.toString
      val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}")
      val knowledge2 = Knowledge(sentenceB,"ja_JP", "{}")
      val paraphrase1 = Knowledge(paraphraseA,"ja_JP", "{}", false)
      val paraphrase2 = Knowledge(paraphraseB,"ja_JP", "{}", false)
      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1))
      val propositionIdForInference = UUID.random.toString
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase2))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = ToposoidUtils.callComponent(inputSentence, conf.getString("SENTENCE_PARSER_JP_WEB_HOST"), "9001", "analyze")
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("0").get.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.havePremiseInGivenProposition).size == 0)
      deleteFeatureVector(propositionId1, "ja_JP", sentenceId1)
    }
  }

  "The specification6" should {
    "returns an appropriate response" in {
      val propositionId1 = UUID.random.toString
      val sentenceId1 = UUID.random.toString
      val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}", false)
      val knowledge2 = Knowledge(sentenceB,"ja_JP", "{}", false)
      val paraphrase1 = Knowledge(paraphraseA,"ja_JP", "{}", false)
      val paraphrase2 = Knowledge(paraphraseB,"ja_JP", "{}", false)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge2))
      val propositionIdForInference = UUID.random.toString
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase2))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = ToposoidUtils.callComponent(inputSentence, conf.getString("SENTENCE_PARSER_JP_WEB_HOST"), "9001", "analyze")
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("0").get.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.havePremiseInGivenProposition).size == 0)
      deleteFeatureVector(propositionId1, "ja_JP", sentenceId1)
    }
  }

  "The specification7" should {
    "returns an appropriate response" in {
      val propositionId1 = UUID.random.toString
      val sentenceId1 = UUID.random.toString
      val sentenceId2 = UUID.random.toString
      val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}", false)
      val knowledge2 = Knowledge(sentenceB,"ja_JP", "{}", false)
      val paraphrase1 = Knowledge(paraphraseA,"ja_JP", "{}", false)
      val paraphrase2 = Knowledge(paraphraseB,"ja_JP", "{}", false)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledge1)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId1, sentenceId2, knowledge2)),
        List.empty[PropositionRelation])
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser)
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser)
      Thread.sleep(5000)

      val propositionIdForInference = UUID.random.toString
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase2))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = ToposoidUtils.callComponent(inputSentence, conf.getString("SENTENCE_PARSER_JP_WEB_HOST"), "9001", "analyze")
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("0").get.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.havePremiseInGivenProposition).size == 0)
      deleteFeatureVector(propositionId1, "ja_JP", sentenceId1)
      deleteFeatureVector(propositionId1, "ja_JP", sentenceId2)
    }
  }

  "The specification8" should {
    "returns an appropriate response" in {
      val propositionId1 = UUID.random.toString
      val propositionId2 = UUID.random.toString
      val sentenceId1 = UUID.random.toString
      val sentenceId2 = UUID.random.toString
      val sentenceId3 = UUID.random.toString
      val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}")
      val knowledge2 = Knowledge(sentenceB,"ja_JP", "{}")
      val paraphrase1 = Knowledge(paraphraseA,"ja_JP", "{}", false)
      val paraphrase2 = Knowledge(paraphraseB,"ja_JP", "{}", false)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1))
      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId2, sentenceId2, knowledge1)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId2, sentenceId3, knowledge2)),
        List.empty[PropositionRelation])
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser)
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser)

      val propositionIdForInference = UUID.random.toString
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase2))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = ToposoidUtils.callComponent(inputSentence, conf.getString("SENTENCE_PARSER_JP_WEB_HOST"), "9001", "analyze")
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("0").get.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.havePremiseInGivenProposition).size == 1)
      deleteFeatureVector(propositionId1, "ja_JP", sentenceId1)
      deleteFeatureVector(propositionId2, "ja_JP", sentenceId2)
      deleteFeatureVector(propositionId2, "ja_JP", sentenceId3)
    }
  }

  "The specification8A" should {
    "returns an appropriate response" in {
      val propositionId1 = UUID.random.toString
      val propositionId2 = UUID.random.toString
      val sentenceId1 = UUID.random.toString
      val sentenceId2 = UUID.random.toString
      val sentenceId3 = UUID.random.toString
      val knowledge1 = Knowledge(sentenceA, "ja_JP", "{}")
      val knowledge2 = Knowledge(sentenceB, "ja_JP", "{}")
      val paraphrase1 = Knowledge(paraphraseA, "ja_JP", "{}", false)
      val paraphrase2 = Knowledge(paraphraseB, "ja_JP", "{}", false)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1))
      registSingleClaim(KnowledgeForParser(propositionId2, sentenceId2, knowledge2))

      val propositionIdForInference = UUID.random.toString
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase2))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = ToposoidUtils.callComponent(inputSentence, conf.getString("SENTENCE_PARSER_JP_WEB_HOST"), "9001", "analyze")
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("0").get.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.havePremiseInGivenProposition).size == 0)
      deleteFeatureVector(propositionId1, "ja_JP", sentenceId1)
      deleteFeatureVector(propositionId2, "ja_JP", sentenceId2)
    }
  }

  "The specification9" should {
    "returns an appropriate response" in {
      val propositionId1 = UUID.random.toString
      val sentenceId1 = UUID.random.toString
      val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}", false)
      //val knowledge2 = Knowledge(sentenceB,"ja_JP", "{}", false)
      //val knowledge3 = Knowledge(sentenceC,"ja_JP", "{}", false)
      val paraphrase1 = Knowledge(paraphraseA,"ja_JP", "{}", false)
      val paraphrase2 = Knowledge(paraphraseB,"ja_JP", "{}", false)
      val paraphrase3 = Knowledge(paraphraseC,"ja_JP", "{}", false)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1))
      val propositionIdForInference = UUID.random.toString
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase1), KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase3))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = ToposoidUtils.callComponent(inputSentence, conf.getString("SENTENCE_PARSER_JP_WEB_HOST"), "9001", "analyze")
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("0").get.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.havePremiseInGivenProposition).size == 0)
      deleteFeatureVector(propositionId1, "ja_JP", sentenceId1)
    }
  }

  "The specification10" should {
    "returns an appropriate response" in {
      val propositionId1 = UUID.random.toString
      val sentenceId1 = UUID.random.toString
      //val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}", false)
      val knowledge2 = Knowledge(sentenceB,"ja_JP", "{}", false)
      //val knowledge3 = Knowledge(sentenceC,"ja_JP", "{}", false)
      val paraphrase1 = Knowledge(paraphraseA,"ja_JP", "{}", false)
      val paraphrase2 = Knowledge(paraphraseB,"ja_JP", "{}", false)
      val paraphrase3 = Knowledge(paraphraseC,"ja_JP", "{}", false)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge2))
      val propositionIdForInference = UUID.random.toString
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase1), KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, UUID.random.toString, paraphrase3))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = ToposoidUtils.callComponent(inputSentence, conf.getString("SENTENCE_PARSER_JP_WEB_HOST"), "9001", "analyze")
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("0").get.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.havePremiseInGivenProposition).size == 0)
      deleteFeatureVector(propositionId1, "ja_JP", sentenceId1)
    }
  }
}
