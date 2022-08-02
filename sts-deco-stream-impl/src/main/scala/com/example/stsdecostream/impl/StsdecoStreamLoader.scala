package com.example.stsdecostream.impl

import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.libs.ws.ahc.AhcWSComponents
import com.example.stsdecostream.api.StsdecoStreamService
import com.example.stsdeco.api.StsdecoService
import com.softwaremill.macwire._

class StsdecoStreamLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new StsdecoStreamApplication(context) {
      override def serviceLocator: NoServiceLocator.type = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new StsdecoStreamApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[StsdecoStreamService])
}

abstract class StsdecoStreamApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with AhcWSComponents {

  // Bind the service that this server provides
  override lazy val lagomServer: LagomServer = serverFor[StsdecoStreamService](wire[StsdecoStreamServiceImpl])

  // Bind the StsdecoService client
  lazy val stsdecoService: StsdecoService = serviceClient.implement[StsdecoService]
}
