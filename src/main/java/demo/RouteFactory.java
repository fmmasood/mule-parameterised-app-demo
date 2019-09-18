package demo;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.mule.api.MuleContext;
import org.mule.api.MuleException;
import org.mule.api.config.ConfigurationBuilder;
import org.mule.api.lifecycle.Disposable;
import org.mule.api.lifecycle.Initialisable;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.config.ConfigResource;
import org.mule.config.spring.SpringXmlConfigurationBuilder;
import org.mule.context.DefaultMuleContextFactory;


public class RouteFactory implements  Initialisable, Disposable{

	private HashMap<String,MuleContext> initialisedContexts;

	private String s3Namespace = "http://www.mulesoft.org/schema/mule/s3";
	private int partnerCount ;

	public RouteFactory() {
		initialisedContexts = new HashMap<String,MuleContext>();
	}

	@Override
	public void initialise() throws InitialisationException {
		// TODO Auto-generated method stub
		System.out.println(this.getClass().getName() + ".initialise(). Total partner count : " + partnerCount );

		int i = 0;
		while(i < partnerCount){
			i++;
			
			try {
				ArrayList<String> config = new ArrayList<String>();
				
				String flowName = System.getProperty("partner-"+i+".flow-name");
				String sourceBucket = System.getProperty("partner-"+i+".source-bucket");
				String destinationBucket = System.getProperty("partner-"+i+".destination-bucket");
				String destinationFolder = System.getProperty("partner-"+i+".destination-folder");
				
				DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
				docFactory.setNamespaceAware(true);
				DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
				
				Document flowTemplate = docBuilder.parse(this.getClass().getClassLoader().getResourceAsStream("flow-templates/list-and-copy-S3-object-flow.xml"));
	
				NodeList flow = flowTemplate.getElementsByTagName("flow");
				NodeList scheduler = flowTemplate.getElementsByTagName("fixed-frequency-scheduler");
				NodeList listObject = flowTemplate.getElementsByTagNameNS(s3Namespace,"list-objects");
				NodeList copyObject = flowTemplate.getElementsByTagNameNS(s3Namespace,"copy-object");
				NodeList deleteObject = flowTemplate.getElementsByTagNameNS(s3Namespace,"delete-object");
				NodeList logger = flowTemplate.getElementsByTagName("logger");
				
				// change flow name 
				flow.item(0).getAttributes().getNamedItem("name").setNodeValue( flowName );

				// change logger message
				logger.item(0).getAttributes().getNamedItem("message").setNodeValue("Triggering " + flowName );

				// change frequency
				scheduler.item(0).getAttributes().getNamedItem("frequency").setNodeValue("${partner-"+i+".polling-frequency}");

				
				// change source bucket
				listObject.item(0).getAttributes().getNamedItem("bucketName").setNodeValue(sourceBucket);
				
				// change destinationBucket and destinationKey(append folder name to key)
				copyObject.item(0).getAttributes().getNamedItem("destinationBucketName").setNodeValue(destinationBucket);
				copyObject.item(0).getAttributes().getNamedItem("destinationKey").setNodeValue("#['" + destinationFolder + "'+" + "payload.getKey()]");
	
				// delete an object from a specific bucket
				deleteObject.item(0).getAttributes().getNamedItem("bucketName").setNodeValue(sourceBucket);
				
				config.add(getStringFromDoc(flowTemplate));
				addFlow(config, flowName);				
				
				
				//System.out.println(getStringFromDoc(flowTemplate));

		
			}catch(Exception e) {
				e.printStackTrace(System.out);
	
			}
			
			
			
		}// end of while	
	}

	
	
	@Override
	public void dispose() {
		// TODO Auto-generated method stub
		System.out.println(this.getClass().getName() + ".dispose(). Total partner count : " + partnerCount );

		Iterator<String> iterator = initialisedContexts.keySet().iterator();
		while ( iterator.hasNext() ) {
			MuleContext muleContext = initialisedContexts.get(iterator.next());
			System.out.println(this.getClass().getName() + "Stopping context with flow ....  " + muleContext.getRegistry().lookupFlowConstructs());
			try {
				muleContext.stop();
				muleContext.dispose();
			} catch (MuleException e) {
				// TODO Auto-generated catch block
				e.printStackTrace(System.out);
				System.out.println(this.getClass().getName() + "Cannot stop context ....  " + muleContext.getRegistry().lookupFlowConstructs());
			}
			
		}
	}

	

	private String getStringFromDoc(Document doc) throws TransformerException {

		DOMSource domSource = new DOMSource(doc);
		StringWriter writer = new StringWriter();
		StreamResult result = new StreamResult(writer);
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer.transform(domSource, result);
		writer.flush();
		return writer.toString();
	}

	private boolean addFlow(ArrayList<String> configs, String flowName) {
		boolean flag = false;

		try {
			// add to mule context
			MuleContext muleContext = new DefaultMuleContextFactory().createMuleContext();
			ConfigResource configResource[] = new ConfigResource[1];

			int i = 0 ;
			
			for (String config : configs) {
				configResource[i] = new ConfigResource(flowName, new ByteArrayInputStream(config.getBytes()) );
				i++;
			}
			ConfigurationBuilder builder = new SpringXmlConfigurationBuilder(configResource);
			builder.configure(muleContext);			
			muleContext.start();

			initialisedContexts.put(flowName, muleContext);

			flag = true;

		}  catch (MuleException me) {
			me.printStackTrace(System.out);
		} 
		return flag;
	}
	
	
	public void setPartnerCount(int partnerCount) {
		this.partnerCount = partnerCount;
	}
}
