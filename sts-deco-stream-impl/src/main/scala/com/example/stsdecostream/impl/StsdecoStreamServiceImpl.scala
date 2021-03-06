package com.example.stsdecostream.impl

import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.example.stsdecostream.api.StsdecoStreamService
import com.example.stsdeco.api.StsdecoService

import scala.concurrent.Future

/**
  * Implementation of the StsdecoStreamService.
  */
class StsdecoStreamServiceImpl(stsdecoService: StsdecoService) extends StsdecoStreamService {
  def stream = ServiceCall { hellos =>
    Future.successful(hellos.mapAsync(8)(stsdecoService.hello(_).invoke()))
  }
}
