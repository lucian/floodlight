package net.floodlightcontroller.flowcache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.ListenerDispatcher;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.flowcache.IFlowCacheService.FCQueryEvType;
import net.floodlightcontroller.flowcache.IFlowReconcileListener;
import net.floodlightcontroller.flowcache.OFMatchReconcile;

import org.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowReconcileManager 
        implements IFloodlightModule, IFlowReconcileService {

    /** The logger. */
    private static Logger logger =
                        LoggerFactory.getLogger(FlowReconcileManager.class);

    /**
     * The list of flow reconcile listeners that have registered to get
     * flow reconcile callbacks. Such callbacks are invoked, for example, when
     * a switch with existing flow-mods joins this controller and those flows
     * need to be reconciled with the current configuration of the controller.
     */
    protected ListenerDispatcher<OFType, IFlowReconcileListener> flowReconcileListeners;
    
    /** Config to enable or disable flowReconcile */
    protected static final String EnableConfigKey = "enable";
    protected boolean flowReconcileEnabled;

    @Override
    public synchronized void addFlowReconcileListener(IFlowReconcileListener listener) {
        flowReconcileListeners.addListener(OFType.FLOW_MOD, listener);

        if (logger.isTraceEnabled()) {
            StringBuffer sb = new StringBuffer();
            sb.append("FlowMod listeners: ");
            for (IFlowReconcileListener l : flowReconcileListeners.getOrderedListeners()) {
                sb.append(l.getName());
                sb.append(",");
            }
            logger.trace(sb.toString());
        }
    }

    @Override
    public synchronized void removeFlowReconcileListener(IFlowReconcileListener listener) {
        flowReconcileListeners.removeListener(listener);
    }
    
    @Override
    public synchronized void clearFlowReconcileListeners() {
        flowReconcileListeners.clearListeners();
    }
    
    /**
     * Reconcile flow.
     *
     * @param ofmRcIn the ofm rc in
     */
    public void reconcileFlow(OFMatchReconcile ofmRcIn) {
    	if (!flowReconcileEnabled) {
    		return;
    	}
    	
        if (logger.isTraceEnabled()) {
            logger.trace("Reconciling flow: {}", ofmRcIn.toString());
        }
        ArrayList<OFMatchReconcile> ofmRcList =
                                            new ArrayList<OFMatchReconcile>();
        ofmRcList.add(ofmRcIn);
        // Run the flow through all the flow reconcile listeners
        IFlowReconcileListener.Command retCmd;
        for (IFlowReconcileListener flowReconciler : flowReconcileListeners.getOrderedListeners()) {
            if (logger.isTraceEnabled()) {
                logger.trace("Reconciling flow: call listener {}", flowReconciler.getName());
            }
            retCmd = flowReconciler.reconcileFlows(ofmRcList);
            if (retCmd == IFlowReconcileListener.Command.STOP) {
                break;
            }
        }
    }
    
    @Override
    public void updateFlowForDestinationDevice(IDevice device,
    		                                   IFlowQueryHandler handler,
    		                                   FCQueryEvType fcEvType) {
    	// NO-OP
    }

    @Override
    public void updateFlowForSourceDevice(IDevice device,
                                          IFlowQueryHandler handler,
                                          FCQueryEvType fcEvType) {
    	// NO-OP
    }
    
    @Override
    public void flowQueryGenericHandler(FlowCacheQueryResp flowResp) {
        if (flowResp.queryObj.evType != FCQueryEvType.GET) {
            OFMatchReconcile ofmRc = new OFMatchReconcile();;
            /* Re-provision these flows */
            for (QRFlowCacheObj entry : flowResp.qrFlowCacheObjList) {
                /* reconcile the flows in entry */
                entry.toOFMatchReconcile(ofmRc, flowResp.queryObj.applInstName,
                                                OFMatchReconcile.ReconcileAction.UPDATE_PATH);
                reconcileFlow(ofmRc);
            }
        }
        return;
    }
    
    // IFloodlightModule

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = 
            new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFlowReconcileService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> 
                                                            getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
        IFloodlightService> m = 
            new HashMap<Class<? extends IFloodlightService>,
                IFloodlightService>();
        m.put(IFlowReconcileService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> 
                                                    getModuleDependencies() {
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        flowReconcileListeners = 
                new ListenerDispatcher<OFType, IFlowReconcileListener>();
        
        Map<String, String> configParam = context.getConfigParams(this);
        String enableValue = configParam.get(EnableConfigKey);
        // Set flowReconcile default to true
        flowReconcileEnabled = true;
        if (enableValue != null &&
            enableValue.equalsIgnoreCase("false")) {
            flowReconcileEnabled = false;
        }
        
        logger.debug("FlowReconcile is {}", flowReconcileEnabled);
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
    }
}

