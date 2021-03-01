package caliban.client

import scala.annotation.tailrec
import caliban.client.__Value.__NullValue

/**
 * Represents an argument in a GraphQL query. Requires an encoder for the argument type.
 */
case class Argument[+A](name: String, value: A)(implicit encoder: ArgEncoder[A]) {
  def toGraphQL(
    useVariables: Boolean,
    variables: Map[String, (__Value, String)],
    filterNullValues: Boolean
  ): (String, Map[String, (__Value, String)]) =
    encoder.encode(value) match {
      case `__NullValue`         => ("", variables)
      case v if filterNullValues =>
        toGraphQL(filterAllNull(v), useVariables, variables)
      case v                     =>
        toGraphQL(v, useVariables, variables)
    }

  private def filterAllNull(v: __Value): __Value =
    v match {
      case __Value.__ListValue(values)   =>
        __Value.__ListValue(values.filterNot(_.equals(__NullValue)))
      case __Value.__ObjectValue(fields) =>
        __Value.__ObjectValue(fields.filterNot(_._2.equals(__NullValue)))
      case v                             =>
        v
    }

  private def toGraphQL(
    v: __Value,
    useVariables: Boolean,
    variables: Map[String, (__Value, String)]
  ): (String, Map[String, (__Value, String)]) =
    if (useVariables) {
      val variableName = Argument.generateVariableName(name, v, variables)
      (s"$name:$$$variableName", variables.updated(variableName, (v, encoder.formatTypeName)))
    } else {
      (s"$name:${v.toString}", variables)
    }
}

object Argument {

  @tailrec
  def generateVariableName(
    name: String,
    value: __Value,
    variables: Map[String, (__Value, String)],
    index: Int = 0
  ): String = {
    val formattedName = if (index > 0) s"$name$index" else name
    variables.get(formattedName) match {
      case None                       => formattedName
      case Some((v, _)) if v == value => formattedName
      case Some(_)                    =>
        generateVariableName(name, value, variables, index + 1)
    }
  }

}
