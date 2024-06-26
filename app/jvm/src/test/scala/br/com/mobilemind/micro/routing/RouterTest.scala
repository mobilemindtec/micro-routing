package br.com.mobilemind.micro.routing

import br.com.mobilemind.micro.routing
import io.micro.routing.{Params, Query}
import io.micro.routing.Path.*
import io.micro.routing.router.Router.{after, before, route, verbs}
import io.micro.routing.router.{Method, RequestBuilder, RouteEntry, RouteInfo, Router}
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.Future

type Headers = Map[String, String]

case class Auth(username: String, token: String)

case class Request(
    method: Method,
    target: String,
    params: Params,
    query: Query,
    matcher: RouteMatcher,
    body: Option[String] = None,
    headers: Headers = Map(),
    auth: Option[Auth] = None
)

case class RequestExtra(body: Option[String] = None, headers: Headers = Map())

trait ResponseBase

case class Response[T](
    status: Int,
    body: Option[T] = None,
    contentType: Option[String] = None
) extends ResponseBase

type ResponseText = Response[String]

object Response:
  def notFound: ResponseText = Response(404)
  def serverError: ResponseText = Response(500)
  def badRequest: ResponseText = Response(400)

  def unauthorized: ResponseText = Response(401)
  def ok: ResponseText = Response(200)

  def apply(status: Int, body: String): ResponseText =
    Response(status, Some(body), None)

  def apply[T](status: Int, body: T): Response[T] =
    Response(status, Some(body), None)

  def apply(status: Int, body: String, contentType: String): ResponseText =
    Response(status, Some(body), Some(contentType))

  def apply[T](status: Int, body: T, contentType: String): Response[T] =
    Response(status, Some(body), Some(contentType))

given RequestBuilder[Request, RequestExtra] with
  override def build(
      routeInfo: RouteInfo,
      extra: Option[RequestExtra]
  ): Request =
    routing.Request(
      routeInfo.method,
      routeInfo.target,
      routeInfo.params,
      routeInfo.query,
      routeInfo.matcher,
      body = extra.flatMap(_.body),
      headers = extra.map(_.headers).getOrElse(Map())
    )

// sbt testOnly *RouterTest
class RouterTest extends AnyFunSuite:

  val users = Map(
    "123456" -> "jonh@gmail.com"
  )

  test("router GET /") {

    val entry = route(Method.Get, root) { (req: Request) =>
      Response(200, "OK", "text/plain")
    }

    val router = Router[Request, ResponseText, RequestExtra](entry)

    router.dispatch(Method.Get, "/") match
      case Some(resp) =>
        assert(resp.body.contains("OK"), "expected response OK")
      case None => fail("resp can't be none")
  }

  test("router GET and POST /") {

    val entry = route(verbs(Method.Get, Method.Post), root) { (req: Request) =>
      Response(200, s"OK ${req.method.verb}", "text/plain")
    }

    val router = Router[Request, ResponseText, RequestExtra](entry)

    router.dispatch(Method.Get, "/") match
      case Some(resp) =>
        assert(resp.body.contains("OK GET"), "expected response OK GET")
      case None => fail("resp can't be none")

    router.dispatch(Method.Post, "/") match
      case Some(resp) =>
        assert(resp.body.contains("OK POST"), "expected response OK POST")
      case None => fail("resp can't be none")
  }

  test("router GET  with auth middleware") {

    val index = route(Method.Get, root) { (req: Request) =>
      Response(200, s"hello ${req.auth.get.username}", "text/plain")
    }

    val auth = before { (req: Request) =>
      req.headers.get("Authorization") match
        case Some(token) =>
          users.get(token) match
            case Some(username) =>
              val auth = Auth(username, token) |> Some.apply
              req.copy(auth = auth)
            case _ => Response.unauthorized
        case _ => Response.unauthorized
    }

    val authIndex = auth ++ index

    val router = Router[Request, ResponseText, RequestExtra](authIndex)
    val extra = RequestExtra(headers = Map("Authorization" -> "123456"))
    router.dispatch(Method.Get, "/", extra) match
      case Some(resp) =>
        assert(
          resp.body.contains("hello jonh@gmail.com"),
          "expected response hello jonh@gmail.com"
        )
      case None => fail("resp can't be none")

    router.dispatch(Method.Get, "/") match
      case Some(resp) =>
        assert(
          resp.status == 401,
          "expected response status 401"
        )
      case None => fail("resp can't be none")

  }

  test("router GET  with auth and validation middleware") {

    val index = route(Method.Get, root) { (req: Request) =>
      Response(200, s"${req.body.get} ${req.auth.get.username}", "text/plain")
    }

    val auth = before { (req: Request) =>
      req.headers.get("Authorization") match
        case Some(token) =>
          users.get(token) match
            case Some(username) =>
              val auth = Auth(username, token) |> Some.apply
              req.copy(auth = auth)
            case _ => Response.unauthorized
        case _ => Response.unauthorized
    }

    val validation = before { (req: Request) =>
      req.body match
        case None => Response.badRequest
        case _    => req
    }

    val authIndex = auth ++ validation ++ index

    val router = Router[Request, ResponseText, RequestExtra](authIndex)
    val extra = RequestExtra(
      body = Some("hello"),
      headers = Map("Authorization" -> "123456")
    )
    router.dispatch(Method.Get, "/", extra) match
      case Some(resp) =>
        assert(
          resp.body.contains("hello jonh@gmail.com"),
          "expected response hello jonh@gmail.com"
        )
      case None => fail("resp can't be none")

    router.dispatch(Method.Get, "/", extra.copy(body = None)) match
      case Some(resp) =>
        assert(
          resp.status == 400,
          "expected response status 400"
        )
      case None => fail("resp can't be none")
  }

  test("router GET  with json middleware") {

    val index = route[Request, ResponseBase](Method.Get, root) {
      (req: Request) =>
        Response(200, Map("name" -> "ricardo"))
    }

    val json = after { (_: Request, resp: ResponseBase) =>
      resp match
        case r: Response[Map[String, String]] =>
          val fields = r.body.map(_.map((k, v) => s"\"$k\": \"$v\""))
          val jsonStr = s"{${fields.get.head}}"
          Response[String](
            status = r.status,
            body = jsonStr,
            contentType = "application/json"
          )
        case _ => resp
    }

    val authIndex = index ++ json

    val router = Router[Request, ResponseBase, RequestExtra](authIndex)

    router.dispatch(Method.Get, "/") match
      case Some(resp: ResponseText) =>
        assert(
          resp.body.contains("{\"name\": \"ricardo\"}"),
          "expected response hello jonh@gmail.com"
        )
      case None => fail("resp can't be none")

  }
