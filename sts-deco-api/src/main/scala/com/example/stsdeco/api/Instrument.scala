package com.example.stsdeco.api

import java.time.LocalDateTime

import play.api.libs.json._

/**
  * This is a Value Type in the context of this project. Instrument are 
  * identified by their properties and are fungible with any other
  * Instrument with the same properties.
  */
trait Instrument{}

object Instrument{
  implicit val format = Format[Instrument](
    Reads { js =>
      val instrumentType = (JsPath \ "instrumentType").read[String].reads(js)
      instrumentType.fold(
        errors => JsError("instrumentType undefined or incorrect"), {
          case "stock"        => (JsPath \ "data").read[Stock].reads(js)
          case "equityoption" => (JsPath \ "data").read[EquityOption].reads(js)
          case "cash"         => (JsPath \ "data").read[Cash].reads(js)
        }
      )
    },
    Writes {
      case stk: Stock =>
        JsObject(
          Seq(
            "instrumentType" -> JsString("stock"),
            "data"      -> Stock.format.writes(stk)
          )
        )
      case opt: EquityOption =>
        JsObject(
          Seq(
            "instrumentType" -> JsString("equity"),
            "data"      -> EquityOption.format.writes(opt)
          )
        )    
      case cash: Cash =>
        JsObject(
          Seq(
            "instrumentType" -> JsString("cash"),
            "data"      -> Cash.format.writes(cash)
          )
        )    
      }
  )
}


final case class ReutersId private (val value: String)

object ReutersId{
      implicit val format: Format[ReutersId] = Json.format
}

final case class Stock(
    ric: ReutersId,
) extends Instrument()

object Stock{
      implicit val format: Format[Stock] = Json.format
}

final case class EquityOption(
    underlying: Instrument,
    strike: Double,
    expiry: LocalDateTime
) extends Instrument

object EquityOption{
      implicit val format: Format[EquityOption] = Json.format
}

final case class Currency private (val value: String)

object Currency{
      implicit val format: Format[Currency] = Json.format
}

final case class Cash(
  denominated: Currency
) extends Instrument

object Cash{
      implicit val format: Format[Cash] = Json.format
}