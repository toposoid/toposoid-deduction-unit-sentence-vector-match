# toposoid-deduction-unit-sentence-vector-match-web
This is a WEB API that works as a microservice within the Toposoid project.
Toposoid is a knowledge base construction platform.(see [Toposoid　Root Project](https://github.com/toposoid/toposoid.git))
This microservice provides the ability to identify the input text as a distributed representation using the [Bert model](https://github.com/google-research/bert) and match it against the knowledge graph. 

[![Test And Build](https://github.com/toposoid/toposoid-deduction-unit-sentence-vector-match-web/actions/workflows/action.yml/badge.svg)](https://github.com/toposoid/toposoid-deduction-unit-sentence-vector-match-web/actions/workflows/action.yml)
* API Image
  * Input
  * <img width="1013" src="https://github.com/toposoid/toposoid-deduction-unit-sentence-vector-match-web/assets/82787843/7313cb49-2d13-4563-8997-e94f9ed548ae">
  * Output
  * <img width="1166" src="https://github.com/toposoid/toposoid-deduction-unit-exact-match-web/assets/82787843/34851da1-5555-47bd-9cf6-6a84dcc8a81a">


## Requirements
* Docker version 20.10.x, or later
* docker-compose version 1.22.x
* The following microservices must be running
  * scala-data-accessor-neo4j-web
  * toposoid/toposoid-common-nlp-japanese-web
  * toposoid/toposoid-common-nlp-english-web
  * toposoid/data-accessor-weaviate-web
  * semitechnologies/weaviate 
  * neo4j

## Recommended Environment For Standalone
* Required: at least 16GB of RAM
* Required: at least 32G of HDD(Total required Docker Image size)
* Please understand that since we are dealing with large models such as LLM, the Dockerfile size is large and the required machine SPEC is high.

## Setup For Standalone
```bssh
docker-compose up
```
The first startup takes a long time until docker pull finishes.

## Usage
```bash
# Please refer to the following for information on registering data to try searching.
# ref. https://github.com/toposoid/toposoid-knowledge-register-web
#for example
curl -X POST -H "Content-Type: application/json" -d '{
    "premiseList": [],
    "premiseLogicRelation": [],
    "claimList": [
        {
            "sentence": "自然界の法則がすべての慣性系で同じように成り立っている。",
            "lang": "ja_JP",
            "extentInfoJson": "{}",
            "isNegativeSentence": false,
            "knowledgeForImages":[]
        }
    ],
    "claimLogicRelation": [
    ]
}
' http://localhost:9002/regist


# Deduction
curl -X POST -H "Content-Type: application/json" -d '{"analyzedSentenceObjects": [
        {
            "nodeMap": {
                "9c344035-3c62-4c06-8849-4f17d28a657c-4": {
                    "nodeId": "9c344035-3c62-4c06-8849-4f17d28a657c-4",
                    "propositionId": "5573eaeb-06c3-4498-a92c-86176eb1f39c",
                    "sentenceId": "9c344035-3c62-4c06-8849-4f17d28a657c",
                    "predicateArgumentStructure": {
                        "currentId": 4,
                        "parentId": 5,
                        "isMainSection": false,
                        "surface": "どの",
                        "normalizedName": "どの",
                        "dependType": "D",
                        "caseType": "連体",
                        "isDenialWord": false,
                        "isConditionalConnection": false,
                        "normalizedNameYomi": "どの",
                        "surfaceYomi": "どの",
                        "modalityType": "-",
                        "parallelType": "-",
                        "nodeType": 1,
                        "morphemes": [
                            "指示詞,連体詞形態指示詞,*,*"
                        ]
                    },
                    "localContext": {
                        "lang": "ja_JP",
                        "namedEntity": "",
                        "rangeExpressions": {
                            "": {}
                        },
                        "categories": {
                            "": ""
                        },
                        "domains": {
                            "": ""
                        },
                        "knowledgeFeatureReferences": []
                    }
                },
                "9c344035-3c62-4c06-8849-4f17d28a657c-0": {
                    "nodeId": "9c344035-3c62-4c06-8849-4f17d28a657c-0",
                    "propositionId": "5573eaeb-06c3-4498-a92c-86176eb1f39c",
                    "sentenceId": "9c344035-3c62-4c06-8849-4f17d28a657c",
                    "predicateArgumentStructure": {
                        "currentId": 0,
                        "parentId": 1,
                        "isMainSection": false,
                        "surface": "自然界の",
                        "normalizedName": "自然",
                        "dependType": "D",
                        "caseType": "ノ格",
                        "isDenialWord": false,
                        "isConditionalConnection": false,
                        "normalizedNameYomi": "しぜんかい?境",
                        "surfaceYomi": "しぜんかいの",
                        "modalityType": "-",
                        "parallelType": "-",
                        "nodeType": 1,
                        "morphemes": [
                            "副詞,*,*,*"
                        ]
                    },
                    "localContext": {
                        "lang": "ja_JP",
                        "namedEntity": "",
                        "rangeExpressions": {
                            "": {}
                        },
                        "categories": {
                            "": "",
                            "界": "抽象物"
                        },
                        "domains": {
                            "": ""
                        },
                        "knowledgeFeatureReferences": []
                    }
                },
                "9c344035-3c62-4c06-8849-4f17d28a657c-3": {
                    "nodeId": "9c344035-3c62-4c06-8849-4f17d28a657c-3",
                    "propositionId": "5573eaeb-06c3-4498-a92c-86176eb1f39c",
                    "sentenceId": "9c344035-3c62-4c06-8849-4f17d28a657c",
                    "predicateArgumentStructure": {
                        "currentId": 3,
                        "parentId": 6,
                        "isMainSection": false,
                        "surface": "なく",
                        "normalizedName": "無い",
                        "dependType": "D",
                        "caseType": "連用",
                        "isDenialWord": false,
                        "isConditionalConnection": false,
                        "normalizedNameYomi": "ない",
                        "surfaceYomi": "なく",
                        "modalityType": "-",
                        "parallelType": "-",
                        "nodeType": 1,
                        "morphemes": [
                            "形容詞,*,イ形容詞アウオ段,基本連用形"
                        ]
                    },
                    "localContext": {
                        "lang": "ja_JP",
                        "namedEntity": "",
                        "rangeExpressions": {
                            "": {}
                        },
                        "categories": {
                            "": ""
                        },
                        "domains": {
                            "": ""
                        },
                        "knowledgeFeatureReferences": []
                    }
                },
                "9c344035-3c62-4c06-8849-4f17d28a657c-6": {
                    "nodeId": "9c344035-3c62-4c06-8849-4f17d28a657c-6",
                    "propositionId": "5573eaeb-06c3-4498-a92c-86176eb1f39c",
                    "sentenceId": "9c344035-3c62-4c06-8849-4f17d28a657c",
                    "predicateArgumentStructure": {
                        "currentId": 6,
                        "parentId": -1,
                        "isMainSection": true,
                        "surface": "成立する。",
                        "normalizedName": "成立",
                        "dependType": "D",
                        "caseType": "文末",
                        "isDenialWord": false,
                        "isConditionalConnection": false,
                        "normalizedNameYomi": "せいりつ",
                        "surfaceYomi": "せいりつする。",
                        "modalityType": "-",
                        "parallelType": "-",
                        "nodeType": 1,
                        "morphemes": [
                            "名詞,サ変名詞,*,*",
                            "動詞,*,サ変動詞,基本形",
                            "特殊,句点,*,*"
                        ]
                    },
                    "localContext": {
                        "lang": "ja_JP",
                        "namedEntity": "",
                        "rangeExpressions": {
                            "": {}
                        },
                        "categories": {
                            "成立": "抽象物"
                        },
                        "domains": {
                            "": ""
                        },
                        "knowledgeFeatureReferences": []
                    }
                },
                "9c344035-3c62-4c06-8849-4f17d28a657c-2": {
                    "nodeId": "9c344035-3c62-4c06-8849-4f17d28a657c-2",
                    "propositionId": "5573eaeb-06c3-4498-a92c-86176eb1f39c",
                    "sentenceId": "9c344035-3c62-4c06-8849-4f17d28a657c",
                    "predicateArgumentStructure": {
                        "currentId": 2,
                        "parentId": 3,
                        "isMainSection": false,
                        "surface": "例外",
                        "normalizedName": "例外",
                        "dependType": "D",
                        "caseType": "未格",
                        "isDenialWord": false,
                        "isConditionalConnection": false,
                        "normalizedNameYomi": "れいがい",
                        "surfaceYomi": "れいがい",
                        "modalityType": "-",
                        "parallelType": "-",
                        "nodeType": 1,
                        "morphemes": [
                            "名詞,普通名詞,*,*"
                        ]
                    },
                    "localContext": {
                        "lang": "ja_JP",
                        "namedEntity": "",
                        "rangeExpressions": {
                            "": {}
                        },
                        "categories": {
                            "例外": "抽象物"
                        },
                        "domains": {
                            "": ""
                        },
                        "knowledgeFeatureReferences": []
                    }
                },
                "9c344035-3c62-4c06-8849-4f17d28a657c-5": {
                    "nodeId": "9c344035-3c62-4c06-8849-4f17d28a657c-5",
                    "propositionId": "5573eaeb-06c3-4498-a92c-86176eb1f39c",
                    "sentenceId": "9c344035-3c62-4c06-8849-4f17d28a657c",
                    "predicateArgumentStructure": {
                        "currentId": 5,
                        "parentId": 6,
                        "isMainSection": false,
                        "surface": "慣性系でも",
                        "normalizedName": "慣性",
                        "dependType": "D",
                        "caseType": "デ格",
                        "isDenialWord": false,
                        "isConditionalConnection": false,
                        "normalizedNameYomi": "かんせいけい",
                        "surfaceYomi": "かんせいけいでも",
                        "modalityType": "-",
                        "parallelType": "AND",
                        "nodeType": 1,
                        "morphemes": [
                            "名詞,普通名詞,*,*"
                        ]
                    },
                    "localContext": {
                        "lang": "ja_JP",
                        "namedEntity": "",
                        "rangeExpressions": {
                            "": {}
                        },
                        "categories": {
                            "": "",
                            "系": "抽象物"
                        },
                        "domains": {
                            "": ""
                        },
                        "knowledgeFeatureReferences": []
                    }
                },
                "9c344035-3c62-4c06-8849-4f17d28a657c-1": {
                    "nodeId": "9c344035-3c62-4c06-8849-4f17d28a657c-1",
                    "propositionId": "5573eaeb-06c3-4498-a92c-86176eb1f39c",
                    "sentenceId": "9c344035-3c62-4c06-8849-4f17d28a657c",
                    "predicateArgumentStructure": {
                        "currentId": 1,
                        "parentId": 6,
                        "isMainSection": false,
                        "surface": "物理法則は",
                        "normalizedName": "物理",
                        "dependType": "D",
                        "caseType": "未格",
                        "isDenialWord": false,
                        "isConditionalConnection": false,
                        "normalizedNameYomi": "ぶつりほうそく",
                        "surfaceYomi": "ぶつりほうそくは",
                        "modalityType": "-",
                        "parallelType": "-",
                        "nodeType": 1,
                        "morphemes": [
                            "名詞,普通名詞,*,*"
                        ]
                    },
                    "localContext": {
                        "lang": "ja_JP",
                        "namedEntity": "",
                        "rangeExpressions": {
                            "": {}
                        },
                        "categories": {
                            "物理": "抽象物",
                            "法則": "抽象物"
                        },
                        "domains": {
                            "物理": "教育・学習;科学・技術",
                            "": ""
                        },
                        "knowledgeFeatureReferences": []
                    }
                }
            },
            "edgeList": [
                {
                    "sourceId": "9c344035-3c62-4c06-8849-4f17d28a657c-5",
                    "destinationId": "9c344035-3c62-4c06-8849-4f17d28a657c-6",
                    "caseStr": "デ格",
                    "dependType": "D",
                    "parallelType": "AND",
                    "hasInclusion": false,
                    "logicType": "-"
                },
                {
                    "sourceId": "9c344035-3c62-4c06-8849-4f17d28a657c-4",
                    "destinationId": "9c344035-3c62-4c06-8849-4f17d28a657c-5",
                    "caseStr": "連体",
                    "dependType": "D",
                    "parallelType": "-",
                    "hasInclusion": false,
                    "logicType": "-"
                },
                {
                    "sourceId": "9c344035-3c62-4c06-8849-4f17d28a657c-3",
                    "destinationId": "9c344035-3c62-4c06-8849-4f17d28a657c-6",
                    "caseStr": "連用",
                    "dependType": "D",
                    "parallelType": "-",
                    "hasInclusion": false,
                    "logicType": "-"
                },
                {
                    "sourceId": "9c344035-3c62-4c06-8849-4f17d28a657c-2",
                    "destinationId": "9c344035-3c62-4c06-8849-4f17d28a657c-3",
                    "caseStr": "未格",
                    "dependType": "D",
                    "parallelType": "-",
                    "hasInclusion": false,
                    "logicType": "-"
                },
                {
                    "sourceId": "9c344035-3c62-4c06-8849-4f17d28a657c-1",
                    "destinationId": "9c344035-3c62-4c06-8849-4f17d28a657c-6",
                    "caseStr": "未格",
                    "dependType": "D",
                    "parallelType": "-",
                    "hasInclusion": false,
                    "logicType": "-"
                },
                {
                    "sourceId": "9c344035-3c62-4c06-8849-4f17d28a657c-0",
                    "destinationId": "9c344035-3c62-4c06-8849-4f17d28a657c-1",
                    "caseStr": "ノ格",
                    "dependType": "D",
                    "parallelType": "-",
                    "hasInclusion": false,
                    "logicType": "-"
                }
            ],
            "knowledgeBaseSemiGlobalNode": {
                "nodeId": "9c344035-3c62-4c06-8849-4f17d28a657c",
                "propositionId": "5573eaeb-06c3-4498-a92c-86176eb1f39c",
                "sentenceId": "9c344035-3c62-4c06-8849-4f17d28a657c",
                "sentence": "自然界の物理法則は例外なくどの慣性系でも成立する。",
                "sentenceType": 1,
                "localContextForFeature": {
                    "lang": "ja_JP",
                    "knowledgeFeatureReferences": []
                }
            },
            "deductionResult": {
                "status": false,
                "coveredPropositionResults": [],
                "havePremiseInGivenProposition": false
            }
        }
    ]
}' http://localhost:9103/execute
```

## For details on Input Json
see below.
* ref. https://github.com/toposoid/toposoid-deduction-admin-web?tab=readme-ov-file#json-details

# Note
* This microservice uses 9103 as the default port.
* If you want to run in a remote environment or a virtual environment, change PRIVATE_IP_ADDRESS in docker-compose.yml according to your environment.
* The memory allocated to Neo4J can be adjusted with NEO4J_dbms_memory_heap_max__size in docker-compose.yml.

## License
toposoid/toposoid-deduction-unit-sentence-vector-match-web is Open Source software released under the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0.html).

## Author
* Makoto Kubodera([Linked Ideal LLC.](https://linked-ideal.com/))

Thank you!
