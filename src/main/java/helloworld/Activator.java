package helloworld;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.dm.Component;
import org.opendaylight.controller.sal.core.ComponentActivatorAbstractBase;
import org.opendaylight.controller.sal.flowprogrammer.IFlowProgrammerService;
import org.opendaylight.controller.sal.packet.IDataPacketService;
import org.opendaylight.controller.sal.packet.IListenDataPacket;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Activatorクラス
 */
public class Activator extends ComponentActivatorAbstractBase {

    /** Logger */
    private static final Logger log = LoggerFactory.getLogger(Activator.class);

    /**
      * このBundleで何のコンポーネントが実装されているかを返却
     */
    @Override
    public Object[] getImplementations() {
        // ここで設定したクラスは、FelixのDependencyManagerに管理されるサービス
        Object[] res = { HelloWorld.class };
        return res;
    }

    /**
      * イベントのコールバック設定と、使用するサービスを指定
     */
    @Override
    public void configureInstance(Component c, Object imp, String containerName) {
        
        log.info("configureInstance is called.");
        
        if (imp.equals(HelloWorld.class)) {
            
            log.debug("begin configuring HelloWorld.");
            
            // OSGiサービスレジストリへのサービス登録
            Dictionary<String, Object> props = new Hashtable<String, Object>();
            props.put("salListenerName", "helloworld");
            // HelloWorldクラスのインタフェースはIListenDataPacketであるため、それを指定
            // configureInstanceを抜けた後、imp＝HelloWorldがsetImplementationされる
            c.setInterface(new String[] { IListenDataPacket.class.getName() }, props);

            // 利用するサービスの設定
            // ISwitchManager：スイッチ情報取得に使用
            c.add(createContainerServiceDependency(containerName)
                    .setService(ISwitchManager.class)
                    .setCallbacks("setSwitchManager", "unsetSwitchManager").setRequired(true));
            
            // IDataPacketService：データ処理、転送に使用
            c.add(createContainerServiceDependency(containerName)
                    .setService(IDataPacketService.class)
                    .setCallbacks("setDataPacketService", "unsetDataPacketService").setRequired(true));

            // IFlowProgrammerService：Flowの操作に使用
            c.add(createContainerServiceDependency(containerName)
                    .setService(IFlowProgrammerService.class)
                    .setCallbacks("setFlowProgrammerService", "unsetFlowProgrammerService").setRequired(true));

            log.debug("end configuring HelloWorld.");

        }
    }
}