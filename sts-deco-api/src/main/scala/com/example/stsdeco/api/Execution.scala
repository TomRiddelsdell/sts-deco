package com.example.stsdeco.api

import play.api.libs.json.Json
import play.api.libs.json.Format

import java.time.LocalDateTime

final case class Execution(
    boughtInstrument: Instrument,
    soldInstrument: Instrument,
    quantity: Double, // number of units of boughtInstrument
    price: Double, // number of units of soldInstrument
    executionTime: LocalDateTime
)

object Execution{
      implicit val format: Format[Execution] = Json.format
}