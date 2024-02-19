package io.micro.routing

import io.micro.routing.Router._
import io.micro.routing.Path._
import io.micro.routing._
import io.micro.routing.Method._
import org.scalatest.funsuite.AnyFunSuite


case class Request(override val uri: String,
                   override val method: String,
                   override val path: Path) extends RouteRequest(uri, method, path)

case class Response(override val params: List[Param] = Nil) extends RouteResponse(params)

class MyController extends ControllerDispatcher:

  type Req = Request
  type Resp = Response

  override def dispatch(req: Req): Resp = ???



class FirstSpec extends AnyFunSuite:

  val token = middleware { (req: Request) => Response() }
  val auth = middleware { (req: Request) => Response() }
  val logger = middleware { (req: Request) => Response() }

  val home = token ++ auth ++ route(Get, root)((req: Request) => Response()) ++ logger

  val user = route(Get, root / "user") { (req: Request) => Response() }

  val userSave = route(Post, user) { (req: Request) => Response() }

  val userGet = route(Post, user / int("id")) { (req: Request) => Response() }

  val userDelete = route(Delete, user, new MyController())

  val routes = prepare(home, user, userSave, userGet, userDelete)

  def testPathParams(uri: String, rt: Route, rs: Seq[Route], expecteds: Param*) =
    val result = process(Method.All, uri, rs)

      result match
        case Some((route, params)) =>

          assert(route.pattern == rt.pattern,
            s"wong route found, -> ${route.pattern}")

          assert(expecteds.size == params.size,
            s"wrong expected params count: ${expecteds.size} != ${params.size}")

          for i <- params.indices do
            val p = params(i)
            val exp = expecteds(i)
            assert(p == exp,
              s"wong param name: ${exp.name} != ${p.name}")


          /*
          route match
            case rt: RouteController[_] =>

              rt.controller match
                case _: Controller =>
                case _: ControllerAsync =>
                case _: ControllerDispatcher =>
                case _: ControllerAsyncDispatcher =>

            case rd: RouteDispatcher[_, _] =>
              rd.dispatch match
                case _:RouteCallback[_, _ ] =>
                case _:RouteAsyncCallback[_, _] =>
          */

        case None =>
          fail(s"route $uri not found")


  test("route compile"){
    assert(home.compile().pattern.get == "^/$", "wrong compile home")
    assert(user.compile().pattern.get == "^/user$", "wrong compile user")
    assert(userSave.compile().pattern.get == "^/user$", "wrong compile userSave")
    assert(userGet.compile().pattern.get == "^/user/([0-9]+)$", "wrong compile userGet")
  }

  test("route process found param int v1") {

    val user = route(Get,
      root / "user" / int("id")) { (req: Request) => Response() }
    val rt = user.compile()
    testPathParams("/user/22", rt, rt :: Nil, ParamInt("id", 22))

  }

  test("route process found param int v2") {

    val user = route(Get,
      root / "user" / int("id")) { (req: Request) => Response() }
    val rt = user.compile()

    testPathParams("/user/22", rt, rt :: Nil, ParamInt("id", 22))
  }

  test("route process found param two int") {

    val user = route(Get,
      root / "user" / int("id") / int("id2")) { (req: Request) => Response() }

    val rt = user.compile()

    assert(rt.pattern.get == "^/user/([0-9]+)/([0-9]+)$", "wrong compile user two path params")

    testPathParams("/user/22/100", rt,
      rt :: Nil, ParamInt("id", 22), ParamInt("id2", 100))

  }

  test("route process found param string") {

    val user = route(Get,
      root / "user" / param("name")) { (req: Request) => Response() }

    val rt = user.compile()

    assert(rt.pattern.get == "^/user/(.+)$", "wrong compile user param string")

    testPathParams("/user/john", rt,
      rt :: Nil, ParamStr("name", "john"))
  }

  test("route process found param string + path") {

    val user = route(Get,
      root / "user" / param("name") / "show") { (req: Request) => Response() }

    val rt = user.compile()

    assert(rt.pattern.get == "^/user/(.+)/show$", "wrong compile user param string")

    testPathParams("/user/john/show", rt,
      rt :: Nil, ParamStr("name", "john"))
  }

  test("route process found param int string") {

    val user = route(Get,
      root / "user" / int("id") / param("name")) { (req: Request) => Response() }

    val rt = user.compile()

    assert(rt.pattern.get == "^/user/([0-9]+)/(.+)$", "wrong compile user two path params")

    testPathParams("/user/22/john", rt,
      rt :: Nil, ParamInt("id", 22), ParamStr("name", "john"))
  }

  test("route process found param tail") {

    val user = route(Get,
      root / "user" / int("id") / tail("paths")) { (req: Request) => Response() }

    val rt = user.compile()

    assert(rt.pattern.get == "^/user/([0-9]+)/(.+)$", "wrong compile user two path params")

    testPathParams("/user/22/john/do/ok", rt,
      rt :: Nil, ParamInt("id", 22), ParamPaths("paths", "john" :: "do" :: "ok" :: Nil))
  }