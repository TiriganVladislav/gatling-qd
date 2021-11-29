package plugin

import com.devexperts.rmi.{RMIClient, RMIOperation, RMIRequest}
import io.gatling.core.session.Expression

import scala.reflect.{ClassTag, classTag}
import scala.reflect.runtime.universe._

//import scala.language.implicitConversions
import io.gatling.core.Predef._

case class QDClientRequestBuilderBase(private val requestName: Expression[String]) {
  def service(service: String) = QDClientRequestBuilderBase0(requestName, service)

}

case class QDClientRequestBuilderBase0(private val requestName: Expression[String],
                                       private val service: String) {
  def method(method: String) = QDClientRequestBuilderBase1(requestName, service, method)
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

  def parameters[P1: ClassTag](param1: P1): QDClientActionBuilder[Res] = {
    val op = RMIOperation.valueOf(service, classTag[Res].runtimeClass.asInstanceOf[Class[Res]], method,
      classTag[P1].runtimeClass.asInstanceOf[Class[P1]])
    val f: RMIClient => RMIRequest[Res] = (client: RMIClient) => {
      client.createRequest(subject, op, param1)
    }

    QDClientActionBuilder(requestName, f)
  }

  def parameters[P1: ClassTag, P2: ClassTag](param1: P1,
                                             param2: P2): QDClientActionBuilder[Res] = {
    val op = RMIOperation.valueOf(service, classTag[Res].runtimeClass.asInstanceOf[Class[Res]], method,
      classTag[P1].runtimeClass.asInstanceOf[Class[P1]],
      classTag[P2].runtimeClass.asInstanceOf[Class[P2]])
    val f: RMIClient => RMIRequest[Res] = (client: RMIClient) => {
      client.createRequest(subject, op, param1, param2)
    }

    QDClientActionBuilder(requestName, f)
  }

  def parameters[P1: ClassTag, P2: ClassTag, P3: ClassTag](param1: P1,
                                                           param2: P2,
                                                           param3: P3): QDClientActionBuilder[Res] = {
    val op = RMIOperation.valueOf(service, classTag[Res].runtimeClass.asInstanceOf[Class[Res]], method,
      classTag[P1].runtimeClass.asInstanceOf[Class[P1]],
      classTag[P2].runtimeClass.asInstanceOf[Class[P2]],
      classTag[P3].runtimeClass.asInstanceOf[Class[P3]])
    val f: RMIClient => RMIRequest[Res] = (client: RMIClient) => {
      client.createRequest(subject, op, param1, param2, param3)
    }

    QDClientActionBuilder(requestName, f)
  }

  def parameters[P1: ClassTag, P2: ClassTag, P3: ClassTag, P4: ClassTag](param1: P1,
                                                                         param2: P2,
                                                                         param3: P3,
                                                                         param4: P4): QDClientActionBuilder[Res] = {
    val op = RMIOperation.valueOf(service, classTag[Res].runtimeClass.asInstanceOf[Class[Res]], method,
      classTag[P1].runtimeClass.asInstanceOf[Class[P1]],
      classTag[P2].runtimeClass.asInstanceOf[Class[P2]],
      classTag[P3].runtimeClass.asInstanceOf[Class[P3]],
      classTag[P4].runtimeClass.asInstanceOf[Class[P4]])


    val f: RMIClient => RMIRequest[Res] = (client: RMIClient) => {
      client.createRequest(subject, op, param1, param2, param3, param4)
    }

    QDClientActionBuilder(requestName, f)
  }

  def parameters[P1: ClassTag, P2: ClassTag, P3: ClassTag, P4: ClassTag, P5: ClassTag](param1: P1,
                                                                                       param2: P2,
                                                                                       param3: P3,
                                                                                       param4: P4,
                                                                                       param5: P5): QDClientActionBuilder[Res] = {
    val op = RMIOperation.valueOf(service, classTag[Res].runtimeClass.asInstanceOf[Class[Res]], method,
      classTag[P1].runtimeClass.asInstanceOf[Class[P1]],
      classTag[P2].runtimeClass.asInstanceOf[Class[P2]],
      classTag[P3].runtimeClass.asInstanceOf[Class[P3]],
      classTag[P4].runtimeClass.asInstanceOf[Class[P4]],
      classTag[P5].runtimeClass.asInstanceOf[Class[P5]])

    val f: RMIClient => RMIRequest[Res] = (client: RMIClient) => {
      client.createRequest(subject, op, param1, param2, param3, param4, param5)
    }

    QDClientActionBuilder(requestName, f)
  }
}