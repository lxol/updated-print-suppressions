package uk.gov.hmrc.ups

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

import akka.actor.ActorSystem
import javax.inject.{Inject, Named, Singleton}
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.metrix.MetricOrchestrator
import uk.gov.hmrc.play.scheduling.ScheduledJob

@Singleton
class PreferencesMain @Inject()(scheduledJobs: Seq[ScheduledJob],
                                metricOrchestrator: MetricOrchestrator,
                                actorSystem: ActorSystem,
                                @Named("refreshInterval") refreshInterval: Long,
                                lifecycle: ApplicationLifecycle)
                               (implicit val ec: ExecutionContext) {

  lifecycle.addStopHook(() => Future{
    actorSystem.terminate()
    metricOrchestrator
  })

  scheduledJobs.foreach(scheduleJob)

  actorSystem.scheduler.schedule(60 seconds, refreshInterval milliseconds){
    metricOrchestrator
      .attemptToUpdateAndRefreshMetrics().map(_.andLogTheResult())
      .recover { case e: RuntimeException => Logger.error(s"An error occurred processing metrics: ${e.getMessage}", e) }
  }

  def scheduleJob(job: ScheduledJob)(implicit ec: ExecutionContext): Unit =
    actorSystem.scheduler.schedule(job.initialDelay, job.interval)(job.execute)

}
