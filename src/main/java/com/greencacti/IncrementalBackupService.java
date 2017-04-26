package com.greencacti;

import com.vmware.vim25.*;
import org.w3c.dom.Element;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.ws.BindingProvider;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * Created by baominw on 4/24/17.
 */
public class IncrementalBackupService {
    private ServiceContent serviceContent;

    private VimPortType vimPort;

    private ManagedObjectReference propertyCollector;

    private ManagedObjectReference morOfSelectedVM;

    private ManagedObjectReference morOfSnapShot;

    private int diskDeviceKey;

    public static void main(String[] args) throws Exception {
        disableCertValidation();

        IncrementalBackupService backupService = new IncrementalBackupService();
        backupService.connectToServer();
        backupService.queryVMProperties();
        backupService.enableCBT();
        backupService.createSnapshot();
        //backupService.recordChangedBlock();
        backupService.recordChangeId();
        backupService.removeSnapShot();
    }

    private static void disableCertValidation() throws Exception {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }
        };

        // Install the all-trusting trust manager
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
    }

    private void connectToServer() throws Exception {
        String url = "https://10.110.170.194/sdk/vimService";

        // Set up the manufactured managed object reference for the ServiceInstance
        ManagedObjectReference svcRef = new ManagedObjectReference();
        svcRef.setType("ServiceInstance");
        svcRef.setValue("ServiceInstance");

        // Create a VimService object to obtain a VimPort binding provider.
        // The BindingProvider provides access to the protocol fields
        // in request/response messages. Retrieve the request context
        // which will be used for processing message requests.
        VimService vimService = new VimService();
        vimPort = vimService.getVimPort();
        Map<String, Object> ctx = ((BindingProvider) vimPort).getRequestContext();

        // Store the Server URL in the request context and specify true
        // to maintain the connection between the client and server.
        // The client API will include the Server's HTTP cookie in its
        // requests to maintain the session. If you do not set this to true,
        // the Server will start a new session with each request.
        ctx.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, url);
        ctx.put(BindingProvider.SESSION_MAINTAIN_PROPERTY, true);

        // Retrieve the ServiceContent object and login
        serviceContent = vimPort.retrieveServiceContent(svcRef);
        vimPort.login(serviceContent.getSessionManager(), "administrator@vsphere.local", "ca$hc0w", null);
        propertyCollector = serviceContent.getPropertyCollector();
    }

    private void queryVMProperties() throws RuntimeFaultFaultMsg, InvalidPropertyFaultMsg {
        // This TraversalSpec traverses Datacenter to vmFolder
        TraversalSpec dc2vmFolder = new TraversalSpec();
        dc2vmFolder.setName("dc2vmFolder");
        dc2vmFolder.setType("Datacenter");
        dc2vmFolder.setPath("vmFolder");
        dc2vmFolder.setSkip(Boolean.FALSE);
        SelectionSpec dc2vmFolderSelectionSpec = new SelectionSpec();
        dc2vmFolderSelectionSpec.setName("folderTS");
        dc2vmFolder.getSelectSet().add(dc2vmFolderSelectionSpec);

        // This TraversalSpec traverses Datacenter to hostFolder
        TraversalSpec dc2hostFolder = new TraversalSpec();
        dc2hostFolder.setName("dc2hostFolder");
        dc2hostFolder.setType("Datacenter");
        dc2hostFolder.setPath("hostFolder");
        dc2hostFolder.setSkip(Boolean.FALSE);
        SelectionSpec dc2hostFolderSelectionSpec = new SelectionSpec();
        dc2hostFolderSelectionSpec.setName("folderTS");
        dc2hostFolder.getSelectSet().add(dc2hostFolderSelectionSpec);

        // This TraversalSpec traverses ResourcePool to resourcePool
        TraversalSpec rp2rp = new TraversalSpec();
        rp2rp.setName("rp2rp");
        rp2rp.setType("ResourcePool");
        rp2rp.setPath("resourcePool");
        rp2rp.setSkip(Boolean.FALSE);

        // This TraversalSpec traverses ComputeResource to resourcePool
        TraversalSpec cr2resourcePool = new TraversalSpec();
        cr2resourcePool.setName("cr2resourcePool");
        cr2resourcePool.setType("ComputeResource");
        cr2resourcePool.setPath("resourcePool");
        cr2resourcePool.setSkip(Boolean.FALSE);

        // This TraversalSpec traverses ComputeResource to host
        TraversalSpec cr2host = new TraversalSpec();
        cr2host.setName("cr2host");
        cr2host.setType("ComputeResource");
        cr2host.setPath("host");
        cr2host.setSkip(Boolean.FALSE);

        // Tie it all together with the Folder TraversalSpec
        TraversalSpec folderTS = new TraversalSpec();
        folderTS.setName("folderTS");
        folderTS.setType("Folder");
        folderTS.setPath("childEntity");
        folderTS.setSkip(Boolean.FALSE);
        SelectionSpec folderTSSelectionSpec = new SelectionSpec();
        folderTSSelectionSpec.setName("folderTS");
        List<SelectionSpec> selectionSpecList = new ArrayList<SelectionSpec>();
        selectionSpecList.add(folderTSSelectionSpec);
        selectionSpecList.add(dc2hostFolder);
        selectionSpecList.add(dc2vmFolder);
        selectionSpecList.add(cr2resourcePool);
        selectionSpecList.add(cr2host);
        selectionSpecList.add(rp2rp);
        folderTS.getSelectSet().addAll(selectionSpecList);

        // create ObjectSpec
        ObjectSpec objectSpec = new ObjectSpec();
        objectSpec.setObj(serviceContent.getRootFolder());
        objectSpec.setSkip(Boolean.FALSE);
        objectSpec.getSelectSet().add(folderTS);

        // create PropertySpec
        /*
        PropertySpec folderSp = new PropertySpec();
        folderSp.setType("Folder");
        folderSp.setAll(Boolean.FALSE);
        folderSp.getPathSet().addAll(Arrays.asList("parent", "name"));
        PropertySpec dcSp = new PropertySpec();
        dcSp.setType("Datacenter");
        dcSp.setAll(Boolean.FALSE);
        dcSp.getPathSet().addAll(Arrays.asList("parent","name"));
        PropertySpec rpSp = new PropertySpec();
        rpSp.setType("ResourcePool");
        rpSp.setAll(Boolean.FALSE);
        rpSp.getPathSet().addAll(Arrays.asList("parent","name","vm"));
        PropertySpec crSp = new PropertySpec();
        crSp.setType("ComputeResource");
        crSp.setAll(Boolean.FALSE);
        crSp.getPathSet().addAll(Arrays.asList("parent","name"));
        */
        PropertySpec vmSp = new PropertySpec();
        vmSp.setType("VirtualMachine");
        vmSp.setAll(Boolean.FALSE);
        vmSp.getPathSet().addAll(Arrays.asList("parent",
                "name",
                "summary.config",
                "snapshot",
                "config.hardware.device"));

        // Tie it all together
        PropertySpec [] pspec = new PropertySpec [] {/*folderSp,
                dcSp,
                rpSp,
                crSp,*/
                vmSp};

        // create PropertyFilterSpec
        PropertyFilterSpec propertyFilterSpec = new PropertyFilterSpec();
        propertyFilterSpec.getPropSet().addAll(Arrays.asList(pspec));
        propertyFilterSpec.getObjectSet().add(objectSpec);

        List<PropertyFilterSpec> propertyFilterSpecList = new ArrayList<PropertyFilterSpec>();
        propertyFilterSpecList.add(propertyFilterSpec);
        List<ObjectContent> objectContentList = retrieveProperties(propertyFilterSpecList);

        // If we get contents back. print them out.
        if (objectContentList != null) {
            ObjectContent objectContent = null;
            ManagedObjectReference mor = null;
            DynamicProperty dynamicProperty = null;

            for (int i = 0; i < objectContentList.size(); i++) {
                objectContent = objectContentList.get(i);
                mor = objectContent.getObj();
                if(mor.getValue().equals("vm-1676")) {
                    System.out.println("Object Type : " + mor.getType());
                    System.out.println("Reference Value : " + mor.getValue());

                    morOfSelectedVM = mor;
                    List<DynamicProperty> dynamicPropertyList = objectContent.getPropSet();
                    if (dynamicPropertyList != null) {
                        for (int j = 0; j < dynamicPropertyList.size(); j++) {
                            dynamicProperty = dynamicPropertyList.get(j);
                            System.out.format("   Property Name : %30s", dynamicProperty.getName());
                            System.out.println("   Property Value : " + dynamicProperty.getVal());
                            if(dynamicProperty.getName().equals("config.hardware.device")) {
                                List<VirtualDevice> virtualDeviceList = ((ArrayOfVirtualDevice)dynamicProperty.getVal()).getVirtualDevice();
                                for(VirtualDevice virtualDevice: virtualDeviceList) {
                                    if(virtualDevice instanceof VirtualDisk) {
                                        VirtualDisk virtualDisk = (VirtualDisk)virtualDevice;
                                        if(virtualDisk.getBacking() instanceof VirtualDiskFlatVer2BackingInfo) {
                                            VirtualDiskFlatVer2BackingInfo virtualDiskFlatVer2BackingInfo =
                                                    (VirtualDiskFlatVer2BackingInfo)virtualDisk.getBacking();
                                            System.out.println(virtualDiskFlatVer2BackingInfo.getFileName());
                                        }
                                        else {
                                            System.out.println("To be added");
                                        }
                                        System.out.println(" - " + virtualDisk.getBacking().getClass());
                                        System.out.println(" - " + virtualDisk.getCapacityInKB());
                                    }

                                }
                            }
                        }
                    }
                }
            }
        } else {
            System.out.println("No Managed Entities retrieved!");
        }
    }

    private void enableCBT() throws Exception{
        VirtualMachineConfigSpec configSpec = new VirtualMachineConfigSpec();
        configSpec.setChangeTrackingEnabled(true);
        ManagedObjectReference taskMoRef =
                vimPort.reconfigVMTask(morOfSelectedVM, configSpec);
        Object[] result = wait(taskMoRef, new String[]{"info.state",
                        "info.error"}, new String[]{"state"},
                new Object[][]{new Object[]{TaskInfoState.SUCCESS,
                        TaskInfoState.ERROR}});

        if (result[0].equals(TaskInfoState.SUCCESS)) {
            System.out.println("Reconfig VM is successful");
        }

        if (result[1] instanceof LocalizedMethodFault) {
            throw new RuntimeException(((LocalizedMethodFault) result[1])
                    .getLocalizedMessage());
        }
    }

    private void recordChangeId() throws Exception {
        ArrayOfVirtualDevice arrayOfVirtualDevice = (ArrayOfVirtualDevice) queryProperties(morOfSnapShot, new String[]{"config.hardware.device"})
                .get("config.hardware.device");
        List<VirtualDevice> virtualDeviceList = arrayOfVirtualDevice.getVirtualDevice();
        for(VirtualDevice virtualDevice: virtualDeviceList) {
            if(virtualDevice instanceof VirtualDisk) {
                VirtualDisk virtualDisk = (VirtualDisk)virtualDevice;
                if(virtualDisk.getCapacityInKB() == 1024) {
                    writeToFile("changeId.txt", ((VirtualDiskFlatVer2BackingInfo)virtualDisk.getBacking()).getChangeId());
                }
            }
        }
    }

    private void recordChangedBlock() throws Exception {
        String lastChangeId = readFromFile("changeId.txt");

        ArrayOfVirtualDevice arrayOfVirtualDevice = (ArrayOfVirtualDevice) queryProperties(morOfSnapShot, new String[]{"config.hardware.device"})
                .get("config.hardware.device");
        List<VirtualDevice> virtualDeviceList = arrayOfVirtualDevice.getVirtualDevice();
        for(VirtualDevice virtualDevice: virtualDeviceList) {
            if(virtualDevice instanceof VirtualDisk) {
                VirtualDisk virtualDisk = (VirtualDisk)virtualDevice;
                if(virtualDisk.getCapacityInKB() == 16384000) {
                    diskDeviceKey = virtualDisk.getKey();
                }
            }
        }

        DiskChangeInfo diskChangeInfo = vimPort.queryChangedDiskAreas(morOfSelectedVM, morOfSnapShot, diskDeviceKey, 0, lastChangeId);
        StringBuilder sb = new StringBuilder();
        for(DiskChangeExtent diskChangeExtent: diskChangeInfo.getChangedArea()) {
            sb.append(diskChangeExtent.getStart() / 512);
            sb.append(" ");
            sb.append(diskChangeExtent.getLength() / 512);
            sb.append("\n");
        }
        writeToFile("changeBlock.txt", sb.toString());
    }

    private void createSnapshot() throws Exception{
        String SnapshotName = "Backup";
        String SnapshotDescription = "Temporary Snapshot for Backup";
        boolean memory_files = false;
        boolean quiesce_filesystem = true;

        ManagedObjectReference taskRef = vimPort.createSnapshotTask(morOfSelectedVM,
                SnapshotName, SnapshotDescription, memory_files, quiesce_filesystem);

        Object[] result = wait(taskRef, new String[]{"info.state",
                        "info.error"}, new String[]{"state"},
                new Object[][]{new Object[]{TaskInfoState.SUCCESS,
                        TaskInfoState.ERROR}});

 		if (result[0].equals(TaskInfoState.SUCCESS)) {
 			System.out.println("Snapshot creation is successful");
 		}

 		if (result[1] instanceof LocalizedMethodFault) {
 			throw new RuntimeException(((LocalizedMethodFault) result[1])
 					.getLocalizedMessage());
 		}


        VirtualMachineSnapshotInfo snapInfo = (VirtualMachineSnapshotInfo) queryProperties(morOfSelectedVM, new String[]{"snapshot"})
                .get("snapshot");
        morOfSnapShot = snapInfo.getCurrentSnapshot();
    }

    private void removeSnapShot() throws Exception{
        ManagedObjectReference taskRef = vimPort.removeSnapshotTask(morOfSnapShot, true, true);
        Object[] result = wait(taskRef, new String[]{"info.state",
                        "info.error"}, new String[]{"state"},
                new Object[][]{new Object[]{TaskInfoState.SUCCESS,
                        TaskInfoState.ERROR}});

        if (result[0].equals(TaskInfoState.SUCCESS)) {
            System.out.println("Snapshot deletion is successful");
        }

        if (result[1] instanceof LocalizedMethodFault) {
            throw new RuntimeException(((LocalizedMethodFault) result[1])
                    .getLocalizedMessage());
        }
    }

    private void writeToFile(String filename, String content) throws Exception{
        BufferedWriter bw = new BufferedWriter(new FileWriter(filename));
        bw.write(content);
        bw.close();
    }

    private String readFromFile(String filename) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(filename));
        String result = br.readLine();
        br.close();
        return result;
    }

    private List<ObjectContent> retrieveProperties(List<PropertyFilterSpec> propertyFilterSpecList)
            throws RuntimeFaultFaultMsg, InvalidPropertyFaultMsg {
        RetrieveOptions retrieveOptions = new RetrieveOptions();

        List<ObjectContent> objectContentList = new ArrayList<ObjectContent>();

        RetrieveResult retrieveResult = vimPort.retrievePropertiesEx(propertyCollector,
                propertyFilterSpecList,
                retrieveOptions);
        if (retrieveResult != null && retrieveResult.getObjects() != null
                && !retrieveResult.getObjects().isEmpty()) {
            objectContentList.addAll(retrieveResult.getObjects());
        }

        String token = null;
        if (retrieveResult != null && retrieveResult.getToken() != null) {
            token = retrieveResult.getToken();
        }

        while (token != null && !token.isEmpty()) {
            retrieveResult = vimPort.continueRetrievePropertiesEx(propertyCollector, token);
            token = null;
            if (retrieveResult != null) {
                token = retrieveResult.getToken();
                if (retrieveResult.getObjects() != null && !retrieveResult.getObjects().isEmpty()) {
                    objectContentList.addAll(retrieveResult.getObjects());
                }
            }
        }

        return objectContentList;
    }

    private Object[] wait(ManagedObjectReference objmor,
                         String[] filterProps, String[] endWaitProps, Object[][] expectedVals)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg,
            InvalidCollectorVersionFaultMsg {
        PropertyFilterSpec spec = createPropertyFilterSpec(objmor, filterProps);
        ManagedObjectReference filterSpecRef = vimPort.createFilter(propertyCollector, spec, true);

        boolean reached = false;
        Object[] endVals = new Object[endWaitProps.length];
        Object[] filterVals = new Object[filterProps.length];
        String stateVal = null;
        UpdateSet updateset = null;
        List<PropertyFilterUpdate> propertyFilterUpdateList = null;
        List<ObjectUpdate> objectUpdateList = null;
        List<PropertyChange> propertyChangeList = null;
        while (!reached) {
            String version = "";
            updateset = vimPort.waitForUpdatesEx(propertyCollector, version, new WaitOptions());
            if (updateset == null || updateset.getFilterSet() == null) {
                continue;
            }

            version = updateset.getVersion();

            // Make this code more general purpose when PropCol changes later.
            propertyFilterUpdateList = updateset.getFilterSet();

            for (PropertyFilterUpdate propertyFilterUpdate : propertyFilterUpdateList) {
                objectUpdateList = propertyFilterUpdate.getObjectSet();
                for (ObjectUpdate objectUpdate : objectUpdateList) {
                    if (objectUpdate.getKind() == ObjectUpdateKind.MODIFY
                            || objectUpdate.getKind() == ObjectUpdateKind.ENTER
                            || objectUpdate.getKind() == ObjectUpdateKind.LEAVE) {
                        propertyChangeList = objectUpdate.getChangeSet();
                        for (PropertyChange propertyChange : propertyChangeList) {
                            updateValues(endWaitProps, endVals, propertyChange);
                            updateValues(filterProps, filterVals, propertyChange);
                        }
                    }
                }
            }

            // Check if the expected values have been reached and exit the loop
            // if done.
            // Also exit the WaitForUpdates loop if this is the case.
            Object expctdval = null;
            for (int chgi = 0; chgi < endVals.length && !reached; chgi++) {
                for (int vali = 0; vali < expectedVals[chgi].length && !reached; vali++) {
                    expctdval = expectedVals[chgi][vali];
                    if (endVals[chgi] == null) {
                        // Do Nothing
                    } else if (endVals[chgi].toString().contains("val: null")) {
                        // Due to some issue in JAX-WS De-serialization getting the information from
                        // the nodes
                        Element stateElement = (Element) endVals[chgi];
                        if (stateElement != null && stateElement.getFirstChild() != null) {
                            stateVal = stateElement.getFirstChild().getTextContent();
                            reached = expctdval.toString().equalsIgnoreCase(stateVal) || reached;
                        }
                    } else {
                        expctdval = expectedVals[chgi][vali];
                        reached = expctdval.equals(endVals[chgi]) || reached;
                        stateVal = "filtervals";
                    }
                }
            }
        }

        // Destroy the filter when we are done.
        Object[] retVal = null;
        try {
            vimPort.destroyPropertyFilter(filterSpecRef);
        } catch (RuntimeFaultFaultMsg e) {
            e.printStackTrace();
        }
        if (stateVal != null) {
            if (stateVal.equalsIgnoreCase("ready")) {
                retVal = new Object[] { HttpNfcLeaseState.READY };
            }
            if (stateVal.equalsIgnoreCase("error")) {
                retVal = new Object[] { HttpNfcLeaseState.ERROR };
            }
            if (stateVal.equals("filtervals")) {
                retVal = filterVals;
            }
        } else {
            retVal = new Object[] { HttpNfcLeaseState.ERROR };
        }
        return retVal;
    }

    private PropertyFilterSpec createPropertyFilterSpec(ManagedObjectReference objmor, String[] filterProps) {
        PropertyFilterSpec spec = new PropertyFilterSpec();
        ObjectSpec objectSpec = new ObjectSpec();
        objectSpec.setObj(objmor);
        objectSpec.setSkip(Boolean.FALSE);
        spec.getObjectSet().add(objectSpec);

        PropertySpec pSpec = new PropertySpec();
        pSpec.getPathSet().addAll(Arrays.asList(filterProps));
        pSpec.setType(objmor.getType());
        spec.getPropSet().add(pSpec);
        return spec;
    }

    private  void updateValues(String[] props, Object[] vals, PropertyChange propertyChange) {
        for (int i = 0; i < props.length; i++) {
            if (propertyChange.getName().lastIndexOf(props[i]) >= 0) {
                if (propertyChange.getOp() == PropertyChangeOp.REMOVE) {
                    vals[i] = "";
                } else {
                    vals[i] = propertyChange.getVal();
                }
            }
        }
    }

    private Map<String, Object> queryProperties(ManagedObjectReference entityMor, String[] props)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        // Create PropertyFilterSpec using the PropertySpec and ObjectPec
        PropertyFilterSpec[] propertyFilterSpecs = {
                new PropertyFilterSpecBuilder()
                        .propSet(
                                // Create Property Spec
                                new PropertySpecBuilder()
                                        .all(Boolean.FALSE)
                                        .type(entityMor.getType())
                                        .pathSet(props)
                        )
                        .objectSet(
                                // Now create Object Spec
                                new ObjectSpecBuilder()
                                        .obj(entityMor)
                        )
        };

        Map<String, Object> retVal = new HashMap<String, Object>();
        List<ObjectContent> oCont =
                vimPort.retrievePropertiesEx(propertyCollector,
                        Arrays.asList(propertyFilterSpecs), new RetrieveOptions()).getObjects();

        if (oCont != null) {
            for (ObjectContent oc : oCont) {
                List<DynamicProperty> dps = oc.getPropSet();
                for (DynamicProperty dp : dps) {
                    retVal.put(dp.getName(), dp.getVal());
                }
            }
        }
        return retVal;
    }
}
