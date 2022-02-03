package servers;

import com.devexperts.logging.Logging;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.impl.RMIEndpointImpl;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMITask;

import java.util.EnumSet;

public class TestServer {
    private static final Logging log = Logging.getLogging(TestServer.class);

    public static void main(String[] args) throws InterruptedException {
        log.configureDebugEnabled(true);
        System.out.println(args[0]); // this will be the address for server to listen args[0] = (:5555)
        initServer(args);
        Thread.sleep(Long.MAX_VALUE);
    }

    public static class EchoService1 extends RMIService<Object[]> {
        private EchoService1() {
            super("echo1");
        }

        @Override
        public void processTask(RMITask<Object[]> task) {
            RMIRequestMessage<Object[]> req = task.getRequestMessage();
            log.info("Received echo task for " + req.getParameters() + " from " + req.getRoute());
            task.complete(req.getParameters().getObject());
        }
    }

    public static class EchoService2 extends RMIService<String> {
        private EchoService2() {
            super("echo2");
        }

        @Override
        public void processTask(RMITask<String> task) {
            RMIRequestMessage<String> req = task.getRequestMessage();
            Object[] params = req.getParameters().getObject();
            log.info("params[0] = " + params[0].toString());
            log.info("params[1] = " + params[1].toString());
            log.info("params[2] = " + params[2].toString());
            log.info("params[3] = " + params[3].toString());
            log.info("Received echo task for " + req.getParameters() + " from " + req.getRoute());
            task.complete(params[3].toString());
        }
    }

    public static void initServer(String[] args) throws InterruptedException {
        String address = args[0];
        DataScheme scheme = SampleScheme.getInstance();
        QDEndpoint endpoint = QDEndpoint.newBuilder()
                .withName("server")
                .withScheme(scheme)
                .withCollectors(EnumSet.of(QDContract.TICKER, QDContract.STREAM, QDContract.HISTORY))
                .build();
        endpoint.getStream().setEnableWildcards(true);

        AgentAdapter.Factory factory = new AgentAdapter.Factory(endpoint, null);
        RMIEndpoint rmiEndpoint =  new RMIEndpointImpl(RMIEndpoint.Side.SERVER, endpoint, factory, null);
        rmiEndpoint.getServer().export(new EchoService1());
        rmiEndpoint.getServer().export(new EchoService2());

        endpoint.initializeConnectorsForAddress(address);
        endpoint.startConnectors();
        Thread generator = new SampleGeneratorThread(endpoint);
        generator.start();
    }
}
