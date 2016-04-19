package edu.uci.ics.cloudberry.gnosis

import java.io.{File, FilenameFilter}

import com.vividsolutions.jts.geom.{Coordinate, Envelope}
import edu.uci.ics.cloudberry.gnosis.USAnnotationHelper.{CityProp, CountyProp, StateProp}
import play.api.libs.json.{JsArray, JsObject, Json, Writes}

class USGeoGnosis(levelPropPathMap: Map[TypeLevel, File], levelGeoPathMap: Map[TypeLevel, File]) {

  import USGeoGnosis._

  val levelShapeMap: Map[TypeLevel, USGeoJSONIndex] = load(levelPropPathMap, levelGeoPathMap)

  private def load(propMap: Map[TypeLevel, File], shapeMap: Map[TypeLevel, File]): Map[TypeLevel, USGeoJSONIndex] = {
    OrderedLevels.map(level => {
      val index = new USGeoJSONIndex()
      val jsArrays = Json.parse(loadSmallJSONFile(propMap.get(level).get)).asInstanceOf[JsArray].value
      loadShape(shapeMap.get(level).get, index, level match {
        case StateLevel => jsArrays.map(_.as[StateProp])
        case CountyLevel => jsArrays.map(_.as[CountyProp])
        case CityLevel => jsArrays.map(_.as[CityProp])
      })
      level -> index
    }).toMap
  }

  lazy val states: Seq[USStateEntity] = {
    levelShapeMap.get(StateLevel).get.entities.map(_.asInstanceOf[USStateEntity])
  }

  lazy val counties: Seq[USCountyEntity] = {
    levelShapeMap.get(CountyLevel).get.entities.map(_.asInstanceOf[USCountyEntity])
  }

  lazy val cities: Seq[USCityEntity] = {
    levelShapeMap.get(CityLevel).get.entities.map(_.asInstanceOf[USCityEntity])
  }

  lazy val stateShapes: IGeoIndex = levelShapeMap.get(StateLevel).get
  lazy val countyShapes: IGeoIndex = levelShapeMap.get(CountyLevel).get
  lazy val cityShapes: IGeoIndex = levelShapeMap.get(CityLevel).get

  def tagRectangle(level: TypeLevel, rectangle: Rectangle): Seq[IUSGeoJSONEntity] = {
    levelShapeMap.get(level).get.search(rectangle.getEnvelopInternal)
  }

  // used in geo tag
  def tagNeighborhood(cityName: String, rectangle: Rectangle): Option[USGeoTagInfo] = {
    val box = new Envelope(rectangle.swLog, rectangle.neLog, rectangle.swLat, rectangle.neLat)
    cities.find(city => city.name == cityName && city.geometry.getEnvelopeInternal.covers(box)).map(USGeoTagInfo(_))
  }

  // used in geo tag
  def tagPoint(longitude: Double, latitude: Double): Option[USGeoTagInfo] = {
    val box = new Envelope(new Coordinate(longitude, latitude))
    val cityOpt = cityShapes.search(box).headOption.map(entity => USGeoTagInfo(entity.asInstanceOf[USCityEntity]))
    if (cityOpt.isDefined) return cityOpt
    countyShapes.search(box).headOption.map(entity => USGeoTagInfo(entity.asInstanceOf[USCountyEntity]))
  }

  // used in geo tag
  def tagCity(cityName: String, stateAbbr: String): Option[USGeoTagInfo] = {
    cities.find(city => city.name == cityName &&
      city.stateName == StateAbbr2FullNameMap.get(stateAbbr).getOrElse("")).map(USGeoTagInfo(_))
  }
}

object USGeoGnosis {

  case class USGeoTagInfo(stateID: Int, stateName: String,
                          countyID: Option[Int], countyName: Option[String],
                          cityID: Option[Int], cityName: Option[String]) {
    override def toString: String = Json.toJson(this).asInstanceOf[JsObject].toString()
  }

  object USGeoTagInfo {
    implicit val writer: Writes[USGeoTagInfo] = Json.writes[USGeoTagInfo]

    def apply(entity: IUSGeoJSONEntity): USGeoTagInfo = {
      entity match {
        case state: USStateEntity => USGeoTagInfo(stateID = state.stateID, stateName = state.name,
                                                  countyID = None, countyName = None, cityID = None, cityName = None)
        case county: USCountyEntity => USGeoTagInfo(stateID = county.stateID, stateName = county.stateName,
                                                    countyID = Some(county.countyID), countyName = Some(county.name),
                                                    cityID = None, cityName = None)
        case city: USCityEntity => USGeoTagInfo(city.stateID, city.stateName, Some(city.countyID), Some(city.countyName),
                                                Some(city.cityID), Some(city.name))
      }
    }
  }

  def loadShape(file: File, index: USGeoJSONIndex, prop: Seq[USAnnotationHelper.HelperProp]) {
    if (file.isDirectory) {
      file.listFiles(new FilenameFilter {
        override def accept(dir: File, name: String): Boolean = name.endsWith(".json")
      }).foreach { file =>
        loadShape(file, index, prop)
      }
    } else {
      val textJson = loadSmallJSONFile(file)
      index.loadShape(textJson, prop)
    }
  }

  val StateAbbr2FullNameMap: Map[String, String] = Map(
    "AL" -> "Alabama",
    "AK" -> "Alaska",
    "AS" -> "American Samoa",
    "AZ" -> "Arizona",
    "AR" -> "Arkansas",
    "CA" -> "California",
    "CO" -> "Colorado",
    "CT" -> "Connecticut",
    "DE" -> "Delaware",
    "DC" -> "District Of Columbia",
    "FM" -> "Federated States Of Micronesia",
    "FL" -> "Florida",
    "GA" -> "Georgia",
    "GU" -> "Guam",
    "HI" -> "Hawaii",
    "ID" -> "Idaho",
    "IL" -> "Illinois",
    "IN" -> "Indiana",
    "IA" -> "Iowa",
    "KS" -> "Kansas",
    "KY" -> "Kentucky",
    "LA" -> "Louisiana",
    "ME" -> "Maine",
    "MH" -> "Marshall Islands",
    "MD" -> "Maryland",
    "MA" -> "Massachusetts",
    "MI" -> "Michigan",
    "MN" -> "Minnesota",
    "MS" -> "Mississippi",
    "MO" -> "Missouri",
    "MT" -> "Montana",
    "NE" -> "Nebraska",
    "NV" -> "Nevada",
    "NH" -> "New Hampshire",
    "NJ" -> "New Jersey",
    "NM" -> "New Mexico",
    "NY" -> "New York",
    "NC" -> "North Carolina",
    "ND" -> "North Dakota",
    "MP" -> "Northern Mariana Islands",
    "OH" -> "Ohio",
    "OK" -> "Oklahoma",
    "OR" -> "Oregon",
    "PW" -> "Palau",
    "PA" -> "Pennsylvania",
    "PR" -> "Puerto Rico",
    "RI" -> "Rhode Island",
    "SC" -> "South Carolina",
    "SD" -> "South Dakota",
    "TN" -> "Tennessee",
    "TX" -> "Texas",
    "UT" -> "Utah",
    "VT" -> "Vermont",
    "VI" -> "Virgin Islands",
    "VA" -> "Virginia",
    "WA" -> "Washington",
    "WV" -> "West Virginia",
    "WI" -> "Wisconsin",
    "WY" -> "Wyoming"
  )

  val StateFullName2AbbrMap: Map[String, String] = StateAbbr2FullNameMap.map(_.swap)
}