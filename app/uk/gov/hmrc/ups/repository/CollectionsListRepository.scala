package uk.gov.hmrc.ups.repository

import scala.concurrent.Future


trait CollectionsListRepository {

  def dropCollections(strings: List[String]): Future[Boolean] = ???

  def getCollectionNames() : Future[List[String]] = ???
  //db.runCommand({listCollections: 1, filter: {"name" : /updated*/} })

}
