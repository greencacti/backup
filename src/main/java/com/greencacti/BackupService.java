package com.greencacti;

import com.vmware.vim25.*;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.ws.BindingProvider;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by baominw on 4/24/17.
 */
public class BackupService {
    private ServiceContent serviceContent;

    private VimPortType vimPort;

    private ManagedObjectReference propertyCollector;

    public static void main(String[] args) throws Exception {
        disableCertValidation();

        BackupService backupService = new BackupService();
        backupService.connectToServer();
        backupService.queryVMProperties();
    }

    private static void disableCertValidation() throws Exception {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
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
        Map<String, Object> ctxt = ((BindingProvider) vimPort).getRequestContext();

        // Store the Server URL in the request context and specify true
        // to maintain the connection between the client and server.
        // The client API will include the Server's HTTP cookie in its
        // requests to maintain the session. If you do not set this to true,
        // the Server will start a new session with each request.
        ctxt.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, url);
        ctxt.put(BindingProvider.SESSION_MAINTAIN_PROPERTY, true);

        // Retrieve the ServiceContent object and login
        serviceContent = vimPort.retrieveServiceContent(svcRef);
        vimPort.login(serviceContent.getSessionManager(), "administrator@vsphere.local", "ca$hc0w", null);
        propertyCollector = serviceContent.getPropertyCollector();
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
                if(mor.getType().equals("VirtualMachine")) {
                    List<DynamicProperty> dynamicPropertyList = objectContent.getPropSet();
                    System.out.println("Object Type : " + mor.getType());
                    System.out.println("Reference Value : " + mor.getValue());

                    if (dynamicPropertyList != null) {
                        for (int j = 0; j < dynamicPropertyList.size(); j++) {
                            dynamicProperty = dynamicPropertyList.get(j);
                            System.out.format("   Property Name : %30s", dynamicProperty.getName());
                            if (dynamicProperty != null) {
                                if (!dynamicProperty.getVal().getClass().isArray()) {
                                    System.out
                                            .println("   Property Value : " + dynamicProperty.getVal());
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
}
