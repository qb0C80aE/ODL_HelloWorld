package helloworld;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opendaylight.controller.sal.action.Action;
import org.opendaylight.controller.sal.action.Output;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.flowprogrammer.Flow;
import org.opendaylight.controller.sal.flowprogrammer.IFlowProgrammerService;
import org.opendaylight.controller.sal.match.Match;
import org.opendaylight.controller.sal.match.MatchType;
import org.opendaylight.controller.sal.packet.Ethernet;
import org.opendaylight.controller.sal.packet.IDataPacketService;
import org.opendaylight.controller.sal.packet.IListenDataPacket;
import org.opendaylight.controller.sal.packet.Packet;
import org.opendaylight.controller.sal.packet.PacketResult;
import org.opendaylight.controller.sal.packet.RawPacket;
import org.opendaylight.controller.sal.utils.EtherTypes;
import org.opendaylight.controller.sal.utils.NetUtils;
import org.opendaylight.controller.sal.utils.Status;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PACKET-INに対する処理を行う
 */
public class HelloWorld implements IListenDataPacket {

    /** IPV4 */
    private static final short DL_TYPE_IPV4 = (short) (0x0800);

    /** Logger */
    private static final Logger log = LoggerFactory.getLogger(HelloWorld.class);

    /** SwitchManager */
    private ISwitchManager switchManager;

    /** DataPacketService */
    private IDataPacketService dataPacketService;

    /** FlowProgrammerService */
    private IFlowProgrammerService flowProgrammerService;

    /** MACtoNodeConnector Map per Switch */
    private Map<Node, Map<Long, NodeConnector>> switchMacToNodeConnectorMap
                            = new HashMap<Node, Map<Long, NodeConnector>>();

    /**
     * SwitchManagerを設定
     * 
     * @param s
     *            SwitchManager
     */
    void setSwitchManager(ISwitchManager s) {
        switchManager = s;
        log.debug("SwitchManager is set.");
    }

    /**
     * SwitchManagerの設定を解除
     * 
     * @param s
     *            SwitchManager
     */
    void unsetSwitchManager(ISwitchManager s) {
        if (switchManager == s) {
            switchManager = null;
        }
        log.debug("SwitchManager is removed.");
    }

    /**
     * DataPacketServiceを設定
     * 
     * @param s
     *            DataPacketService
     */
    void setDataPacketService(IDataPacketService s) {
        dataPacketService = s;
        log.debug("DataPacketService is set.");
    }

    /**
     * DataPacketServiceの設定を解除
     * 
     * @param s
     *            DataPacketService
     */
    void unsetDataPacketService(IDataPacketService s) {
        if (dataPacketService == s) {
            dataPacketService = null;
        }
        log.debug("DataPacketService is removed.");
    }

    /**
     * FlowProgrammerServiceを設定
     * 
     * @param s
     *            FlowProgrammerService
     */
    void setFlowProgrammerService(IFlowProgrammerService s) {
        flowProgrammerService = s;
        log.debug("FlowProgrammerService is set.");
    }

    /**
     * FlowProgrammerServiceの設定を解除
     * 
     * @param s
     *            FlowProgrammerService
     */
    void unsetFlowProgrammerService(IFlowProgrammerService service) {
        if (flowProgrammerService == service) {
            flowProgrammerService = null;
        }
        log.debug("FlowProgrammerService is removed.");
    }

    /**
     * 指定パケットを入力ポート以外にfloodingする
     * 
     * @param inPkt
     *            出力する対象パケット
     * @throws Exception
     */
    private void flood(RawPacket inPkt) throws Exception {

        log.debug("begin flooding.");

        NodeConnector ingressConnector = inPkt.getIncomingNodeConnector();
        Node incomingNode = ingressConnector.getNode();

        Set<NodeConnector> nodeConnectorSet = switchManager
                .getUpNodeConnectors(incomingNode);

        for (NodeConnector nodeConnector : nodeConnectorSet) {
            if (!(nodeConnector.equals(ingressConnector))) {
                forward(nodeConnector, inPkt);
            }
        }

        log.debug("end flooding.");

    }

    /**
     * 指定スイッチの、指定MACアドレスが存在するポートにパケットを送信する
     * 
     * @param egressConnector
     *            出力ポート
     * @param inPkt
     *            出力する対象パケット
     * @throws Exception
     */
    private void forward(NodeConnector egressConnector, RawPacket inPkt)
            throws Exception {

        log.debug("begin forwarding.");

        RawPacket outPkt = new RawPacket(inPkt);
        // 出力ポートの設定
        outPkt.setOutgoingNodeConnector(egressConnector);
        // PACKET_OUT処理
        dataPacketService.transmitDataPacket(outPkt);

        log.debug("sent to: " + egressConnector.getNode().getNodeIDString()
                + "/" + egressConnector.getNodeConnectorIDString());

        log.debug("end forwarding.");

    }

    /**
     * MACアドレスの学習を行う
     * 
     * @param sourceMacAddress
     *            入力MACアドレス
     * @param ingressConnector
     *            入力ポート
     * @param node
     *            対象スイッチ
     * @return 対象スイッチのMACアドレステーブル
     */
    private Map<Long, NodeConnector> learnMac(Long sourceMacAddress,
            NodeConnector ingressConnector, Node node) {

        // TODO:エイジアウト等で削除の実装が必要
        Map<Long, NodeConnector> macToNodeConnectorMap = null;
        if (switchMacToNodeConnectorMap.containsKey(node)) {
            macToNodeConnectorMap = switchMacToNodeConnectorMap.get(node);
        } else {
            macToNodeConnectorMap = new HashMap<Long, NodeConnector>();
            switchMacToNodeConnectorMap.put(node, macToNodeConnectorMap);
            log.info("New switchMacToNodeConnectorMap is created: "
                    + node.toString());
        }

        if (!(macToNodeConnectorMap.containsKey(sourceMacAddress))) {
            macToNodeConnectorMap.put(sourceMacAddress, ingressConnector);
            log.info("MacToNodeConnectorPair is registered: "
                    + sourceMacAddress + " / " + ingressConnector.toString());
        } else {
            NodeConnector registeredConnector = macToNodeConnectorMap
                    .get(sourceMacAddress);
            if (!registeredConnector.equals(ingressConnector)) {
                macToNodeConnectorMap.remove(sourceMacAddress);
                macToNodeConnectorMap.put(sourceMacAddress, ingressConnector);
                log.info("MacToNodeConnectorPair is updated: "
                        + sourceMacAddress + " / "
                        + ingressConnector.toString());
            }
        }

        return macToNodeConnectorMap;

    }

    @Override
    public synchronized PacketResult receiveDataPacket(RawPacket inPkt) {

        try {

            // Nodeはスイッチ、NodeConnectorはスイッチのポートを表す
            NodeConnector ingressConnector = inPkt.getIncomingNodeConnector();

            // 問い合わせをかけてきたスイッチ
            Node node = ingressConnector.getNode();

            log.info("PACKET-IN occured.");
            log.info("input: " + ingressConnector.getNode().getNodeIDString()
                    + "/" + ingressConnector.getNodeConnectorIDString());

            // パケットの復号化
            Packet decodedPacket = dataPacketService.decodeDataPacket(inPkt);

            // イーサネットフレーム以外は処理しない
            if (!(decodedPacket instanceof Ethernet)) {
                log.debug("inPkt is not an Ethernet Frame.");
                // このサービスでは、このパケットは処理しない
                return PacketResult.IGNORED;
            }

            Ethernet ethernetFrame = (Ethernet) (decodedPacket);

            // LLDPは無視
            if (ethernetFrame.getEtherType() == EtherTypes.LLDP.shortValue()) {
                log.debug("inPkt is a LLDP Packet.");
                // このサービスでは、このパケットは処理しない
                return PacketResult.IGNORED;
            }

            // IPv6は無視
            if (ethernetFrame.getEtherType() == EtherTypes.IPv6.shortValue()) {
                log.debug("inPkt is an IPv6 Packet.");
                // このサービスでは、このパケットは処理しない
                return PacketResult.IGNORED;
            }

            Long sourceMacAddress = NetUtils.byteArray6ToLong(ethernetFrame
                    .getSourceMACAddress());
            Long destinationMacAddress = NetUtils
                    .byteArray6ToLong(ethernetFrame.getDestinationMACAddress());

            // MACアドレスの学習
            Map<Long, NodeConnector> macToNodeConnectorMap = learnMac(
                    sourceMacAddress, ingressConnector, node);

            // ブロードキャストの場合
            if (NetUtils.isBroadcastMACAddr(ethernetFrame
                    .getDestinationMACAddress())) {
                // floodingする
                flood(inPkt);
                // 他のサービスが処理する可能性があるため、KEEP_PROCESSINGを返却
                // （ArpHandlerは停止させて試す必要があるため、CONSUMEでもよい）
                return PacketResult.KEEP_PROCESSING;
            }

            // MACに対するOUTPUTのフローを作成して設定
            // 条件の定義
            Match match = new Match();
            match.setField(MatchType.DL_TYPE, DL_TYPE_IPV4);
            match.setField(MatchType.DL_SRC,
                    ethernetFrame.getSourceMACAddress());
            match.setField(MatchType.DL_DST,
                    ethernetFrame.getDestinationMACAddress());

            // アクションの定義
            List<Action> actions = new LinkedList<Action>();
            // FORWARDには出力先が必要なため、出力ポートを指定したOutputアクションを設定
            actions.add(new Output(macToNodeConnectorMap
                    .get(destinationMacAddress)));

            // 条件とアクションからフローを作成
            Flow flow = new Flow(match, actions);

            // FlowProgrammerServiceを経由してスイッチにフローを設定
            Status status = flowProgrammerService.addFlow(node, flow);
            if (!status.isSuccess()) {
                log.error("Setting new flows to the target switch is failed: "
                        + status.getDescription());
                // パケットを消費済みとしてマークする(この後他のサービスがこのパケットを処理することはない)
                return PacketResult.CONSUME;
            }

            log.debug("Setting new flows to the target switch is successed.");

            // 初回パケットのPACKET_OUTを実行
            forward(macToNodeConnectorMap.get(destinationMacAddress), inPkt);

            // パケットを消費済みとしてマークする(この後他のサービスがこのパケットを処理することはない)
            return PacketResult.CONSUME;

        } catch (Exception e) {
            e.printStackTrace();
            return PacketResult.IGNORED;
        }

    }

}
