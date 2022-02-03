package plugin.action

import com.devexperts.rmi.{RMIClient, RMIOperation, RMIRequest}
import io.gatling.commons.validation.Validation
import io.gatling.core.ValidationImplicits
import io.gatling.core.session._
import plugin.action

import scala.reflect.{ClassTag, classTag}

//import scala.language.implicitConversions

case class QDClientRequestBuilderBase(private val requestName: Expression[String]) extends ValidationImplicits {
  def service(service: String): QDClientRequestBuilderBase0 =
    QDClientRequestBuilderBase0(requestName, service)

}

case class QDClientRequestBuilderBase0(private val requestName: Expression[String],
                                       private val service: String) {
  def method(method: String): QDClientRequestBuilderBase1
  = QDClientRequestBuilderBase1(requestName, service, method)
}

case class QDClientRequestBuilderBase1(private val requestName: Expression[String],
                                       private val service: String,
                                       private val method: String) {
  def returnType[Res: ClassTag]: QDClientRequestBuilderBase2[Res] =
    QDClientRequestBuilderBase2(requestName, service, method)
}

case class QDClientRequestBuilderBase2[Res: ClassTag](private val requestName: Expression[String],
                                                      private val service: String,
                                                      private val method: String,
                                                      private val subject: Any = null) {
  def subject(subj: Any): QDClientRequestBuilderBase2[Res] = copy(subject = subj)

  def parameters[P1: ClassTag](param1: Expression[P1]): QRMIActionBuilder[Res] = {
    val op = RMIOperation.valueOf(service, classTag[Res].runtimeClass.asInstanceOf[Class[Res]], method,
      classTag[P1].runtimeClass.asInstanceOf[Class[P1]])
    val f: (RMIClient, Session) => Validation[RMIRequest[Res]] = (client, session) => {
      for {
        p1 <- param1(session)
      }
      yield {
        client.createRequest(subject, op, p1)
      }
    }

    action.QRMIActionBuilder(requestName, f)
  }

  def parameters[P1: ClassTag, P2: ClassTag](param1: Expression[P1],
                                             param2: Expression[P2]): QRMIActionBuilder[Res] = {
    val op = RMIOperation.valueOf(service, classTag[Res].runtimeClass.asInstanceOf[Class[Res]], method,
      classTag[P1].runtimeClass.asInstanceOf[Class[P1]],
      classTag[P2].runtimeClass.asInstanceOf[Class[P2]])

    val f: (RMIClient, Session) => Validation[RMIRequest[Res]] = (client, session) => {
      for {
        p1 <- param1(session)
        p2 <- param2(session)
      }
      yield {
        client.createRequest(subject, op, p1, p2)
      }
    }

    action.QRMIActionBuilder(requestName, f)
  }

  def parameters[P1: ClassTag, P2: ClassTag, P3: ClassTag](param1: Expression[P1],
                                                           param2: Expression[P2],
                                                           param3: Expression[P3]): QRMIActionBuilder[Res] = {
    val op = RMIOperation.valueOf(service, classTag[Res].runtimeClass.asInstanceOf[Class[Res]], method,
      classTag[P1].runtimeClass.asInstanceOf[Class[P1]],
      classTag[P2].runtimeClass.asInstanceOf[Class[P2]],
      classTag[P3].runtimeClass.asInstanceOf[Class[P3]])

    val f: (RMIClient, Session) => Validation[RMIRequest[Res]] = (client, session) => {
      for {
        p1 <- param1(session)
        p2 <- param2(session)
        p3 <- param3(session)
      }
      yield {
        client.createRequest(subject, op, p1, p2, p3)
      }
    }

    action.QRMIActionBuilder(requestName, f)
  }

  def parameters[P1: ClassTag, P2: ClassTag, P3: ClassTag, P4: ClassTag](param1: Expression[P1],
                                                                         param2: Expression[P2],
                                                                         param3: Expression[P3],
                                                                         param4: Expression[P4]):
  QRMIActionBuilder[Res] = {
    val op = RMIOperation.valueOf(service, classTag[Res].runtimeClass.asInstanceOf[Class[Res]], method,
      classTag[P1].runtimeClass.asInstanceOf[Class[P1]],
      classTag[P2].runtimeClass.asInstanceOf[Class[P2]],
      classTag[P3].runtimeClass.asInstanceOf[Class[P3]],
      classTag[P4].runtimeClass.asInstanceOf[Class[P4]])


    val f: (RMIClient, Session) => Validation[RMIRequest[Res]] = (client, session) => {
      for {
        p1 <- param1(session)
        p2 <- param2(session)
        p3 <- param3(session)
        p4 <- param4(session)
      }
      yield {
        client.createRequest(subject, op, p1, p2, p3, p4)
      }
    }

    action.QRMIActionBuilder(requestName, f)
  }

  def parameters[P1: ClassTag, P2: ClassTag, P3: ClassTag, P4: ClassTag, P5: ClassTag](param1: Expression[P1],
                                                                                       param2: Expression[P2],
                                                                                       param3: Expression[P3],
                                                                                       param4: Expression[P4],
                                                                                       param5: Expression[P5]):
  QRMIActionBuilder[Res] = {
    val op = RMIOperation.valueOf(service, classTag[Res].runtimeClass.asInstanceOf[Class[Res]], method,
      classTag[P1].runtimeClass.asInstanceOf[Class[P1]],
      classTag[P2].runtimeClass.asInstanceOf[Class[P2]],
      classTag[P3].runtimeClass.asInstanceOf[Class[P3]],
      classTag[P4].runtimeClass.asInstanceOf[Class[P4]],
      classTag[P5].runtimeClass.asInstanceOf[Class[P5]])


    val f: (RMIClient, Session) => Validation[RMIRequest[Res]] = (client, session) => {
      for {
        p1 <- param1(session)
        p2 <- param2(session)
        p3 <- param3(session)
        p4 <- param4(session)
        p5 <- param5(session)
      }
      yield {
        client.createRequest(subject, op, p1, p2, p3, p4, p5)
      }
    }

    action.QRMIActionBuilder(requestName, f)
  }
}