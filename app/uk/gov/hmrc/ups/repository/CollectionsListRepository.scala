/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.ups.repository

import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DefaultDB
import reactivemongo.api.collections.bson.BSONCollection

import scala.concurrent.{ExecutionContext, Future}

class CollectionsListRepository(implicit db: () => DefaultDB) {

  def dropCollection(collectionName: String)(implicit ec: ExecutionContext): Future[Unit] =
    db().collection[BSONCollection](collectionName).drop()

  private def listCollectionNames(predicate: String => Boolean)(implicit ec: ExecutionContext): Future[List[String]] =
    db().collectionNames.map(_.filter(predicate))

  def upsCollectionNames(implicit ec: ExecutionContext): Future[List[String]] = listCollectionNames(_.startsWith("updated"))

}
object CollectionsListRepository extends  MongoDbConnection  {
  def apply(): CollectionsListRepository = new CollectionsListRepository
}