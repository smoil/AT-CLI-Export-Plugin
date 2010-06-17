package org.archiviststoolkit.plugin.cli;

import org.archiviststoolkit.ApplicationFrame;
import org.archiviststoolkit.util.StringHelper;
import org.archiviststoolkit.exporter.EADExport;
import org.archiviststoolkit.editor.ArchDescriptionFields;
import org.archiviststoolkit.model.Resources;
import org.archiviststoolkit.mydomain.DomainAccessObject;
import org.archiviststoolkit.mydomain.DomainEditorFields;
import org.archiviststoolkit.mydomain.DomainObject;
import org.archiviststoolkit.mydomain.ResourcesDAO;
import org.archiviststoolkit.plugin.ATPlugin;
import org.archiviststoolkit.swing.InfiniteProgressPanel;
import org.java.plugin.Plugin;
import org.hibernate.Session;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;

/* Based off the cli plugin code written by the person below 
 * Currently only supports EAD export of resources that have
 * a finding aid status of "Complete"
 * */

/**
 * Created by IntelliJ IDEA.
 * User: nathan
 * Date: Apr 19, 2010
 * Time: 8:01:58 PM
 *
 * This is a sample plugin which demonstrate how to develope
 * Command Line Interface, CLI, plugins for the AT. It also demonstrate
 * techniques for saving memory which is critical when batch processing
 * large amount of records.
 */
public class CLIExportPlugin extends Plugin implements ATPlugin {
    /**
     *  get the category(ies) this plugin belongs to. For plugins that
     *  are only used through the CLI then the below is fine. Additional
     *  categories can be defined if this plugin is also to be used with
     *  the main AT program
     */
    public String getCategory() {
        //return ATPlugin.CLI_CATEGORY + " " + ATPlugin.DEFAULT_CATEGORY;
        return ATPlugin.CLI_CATEGORY;
    }

    // get the name of this plugin
    public String getName() {
        return "CLI Export";
    }

    /**
     * Method to actually execute the logic of the plugin. It is
     * automatically called by the atcli program so it needs to be
     * implemented
     *
     * @param task The task that is passed in as the second argument in
     * the command line parameters
     */
    public void doTask(String task) {
    	// show the command line parameters
        String[] params = org.archiviststoolkit.plugin.ATPluginFactory.getInstance().getCliParameters();
        for(int i = 0; i < params.length; i++) {
            System.out.println("Parameter " + (i+1) + " = " + params[i]);
        }
        
        java.util.Hashtable optionsAndArgs = new java.util.Hashtable();
        for(int i=3; i<params.length;i++){
        	if(params[i].startsWith("-"))
        		optionsAndArgs.putAll(addParams(params, i));
        }        
        
        DomainAccessObject access = new ResourcesDAO();

        // get the list of all resources from the database
        java.util.List<Resources> resources;
        java.util.List<Resources> completedResources;

        try {
            resources = (java.util.List<Resources>) access.findAll();
        } catch (Exception e) {
            resources = null;
        }

        int recordCount = resources.size();
        
        // Get the path where to export the files to from the command line arguments
        String exportRootPath = params[2];

        //dummy progress panel to pass into convertResourceToFile
        InfiniteProgressPanel fakePanel = new InfiniteProgressPanel();

        // print out the resource identifier and title and export as ead
        System.out.println("Exporting EADs ");

        // Get the runtime for clearing memory
        Runtime runtime = Runtime.getRuntime();

        // get the ead exporter
        EADExport ead = new EADExport();
        
        // create an index file of all resources
        java.io.File indexFile = new java.io.File(exportRootPath + "resourceIndex.txt");
        try{
        	indexFile.createNewFile();
        }catch(Exception e){
        	System.out.println("Failed to create index file");
        }
        
        // populate index file
       	try{
        	java.io.FileWriter fstream = new java.io.FileWriter(indexFile);
        	java.io.BufferedWriter out = new java.io.BufferedWriter(fstream);
        	for(int i=0; i< recordCount; i++){
        		out.write(resources.get(i).getResourceIdentifier().toString()+'\t');
        		String status = resources.get(i).getFindingAidStatus();
        		status = status.replace("\n", "");
        		status = status.replace("\r", "");
        		if(status.length() > 0){
        			out.write(status+'\t');
        		}else{
        			out.write("--------"+'\t');
        		}
        		// remove newlines / carriage returns
        		String title = resources.get(i).getTitle().replaceAll("\\n","").replaceAll("\\r","");
        		if(title.length() > 0){
        			out.write(title+'\t');
        		}else{
        			out.write("--------"+'\t');
        		}
        		//this doesnt work
        		out.write(resources.get(i).getLastUpdated().toString());
        		out.newLine();
        	}
        }catch(Exception e){
        	System.out.println(e);
        }
        
        for(int i = 0; i < recordCount; i++) {
        	if( filterOnOptions(resources.get(i), optionsAndArgs)){        		
	            try {
	                // load the full resource from database using a long session
	                Resources resource = (Resources)access.findByPrimaryKeyLongSession(resources.get(i).getIdentifier());
	
	                System.out.println(resource.getResourceIdentifier() + " : " + resource.getTitle());
	
	                String fileName = StringHelper.removeInvalidFileNameCharacters(resource.getResourceIdentifier());
	                java.io.File file = new java.io.File(exportRootPath + fileName + ".xml");
	                file.createNewFile();
	                ead.convertResourceToFile(resource, file, fakePanel, false, true, true, true);
	
	                
	            } catch (Exception e) {
	                System.out.println(e);
	            }
	
	            // close the long session. This is critical to saving memory. If it's
	            // left open then hinernate caches the resource records even though
	            // we don't need them anymore
	            access.getLongSession().close(); // close the connection
	
	            
        	}
        	// since we no longer need this resource, set it to null
            // close the session in an attemp to save memory
            resources.set(i, null);
            
            // run GC to clear some memory after 10 exports, not sure if this is
            // really needed but running GC cost little in time so might as well?
            runtime.gc();
        }

        System.out.println("Finished Exporting ...");
    }

    /**
     * Method to get the list of specific task the plugin can perform. Only
     * the task at position 0 in the string array is checked in CLI plugins
     *
     * @return String array containing the task(s) the plugin is registered
     * to handel.
     */
    public String[] getTaskList() {
        String[] tasks = {"ead"};

        return tasks;
    }
    
    //Pass in params array and an index to that array pointing a parameter type (like -authors)
    //Returns a hash with the parameter type as the index and an array of options
    private java.util.Hashtable addParams(String[] args, int index){
    	// create an array to store sub arguments
    	java.util.List<String> subArgs = new java.util.ArrayList();
    	int n = index + 1;
    	try {
    		while(!args[n].startsWith("-")){
    			subArgs.add(args[n]);
    			n++;
    		}
    	}catch(Exception e){}
    	java.util.Hashtable returnVal = new java.util.Hashtable();
    	returnVal.put(args[index], subArgs);
    	return returnVal;
    }
    
    // Returns true is resource is qualified by options
    // If option is not present, allow it to pass
    // If option is present, deny unless check passes
    // Currently options are ANDED, if any check fails they all fail
    private boolean filterOnOptions(Resources resource, java.util.Hashtable options){
    	
    	// finding aid status options
    	boolean fas_proceed = true;
    	if(options.containsKey("-fas")){
    		fas_proceed = false;
    		if( ((java.util.List<String>)options.get("-fas")).contains(resource.getFindingAidStatus()) )
    			fas_proceed = true;
    	}
    	
    	// authors
    	boolean author_proceed = true;
    	if(options.containsKey("-author")){
    		author_proceed = false;
    		if( ((java.util.List<String>)options.get("-author")).contains(resource.getAuthor()) )
    			author_proceed = true;
    	}
    	
    	// ead fa unique id #
    	boolean eadfauid_proceed = true;
    	if(options.containsKey("-eadfauid")){
    		eadfauid_proceed = false;
    		if( ((java.util.List<String>)options.get("-eadfauid")).contains(resource.getEadFaUniqueIdentifier()) )
    			eadfauid_proceed = true;
    	}
    	
    	// resource ID #
    	boolean rid_proceed = true;
    	if(options.containsKey("-rid")){
    		rid_proceed = false;
    		if( ((java.util.List<String>)options.get("-rid")).contains(resource.getResourceIdentifier()) )
    			rid_proceed = true;
    	}
    	
    	// resource ID # 1
    	boolean rid1_proceed = true;
    	if(options.containsKey("-rid1")){
    		rid1_proceed = false;
    		if( ((java.util.List<String>)options.get("-rid1")).contains(resource.getResourceIdentifier1()) )
    			rid1_proceed = true;
    	}
    	
    	// resource ID # 2
    	boolean rid2_proceed = true;
    	if(options.containsKey("-rid2")){
    		rid2_proceed = false;
    		if( ((java.util.List<String>)options.get("-rid2")).contains(resource.getResourceIdentifier2()) )
    			rid2_proceed = true;
    	}
    	
    	// resource ID # 3
    	boolean rid3_proceed = true;
    	if(options.containsKey("-rid3")){
    		rid3_proceed = false;
    		if( ((java.util.List<String>)options.get("-rid3")).contains(resource.getResourceIdentifier3()) )
    			rid3_proceed = true;
    	}
    	
    	// resource ID # 4
    	boolean rid4_proceed = true;
    	if(options.containsKey("-rid4")){
    		rid4_proceed = false;
    		if( ((java.util.List<String>)options.get("-rid4")).contains(resource.getResourceIdentifier4()) )
    			rid4_proceed = true;
    	}
    	
    	// repository Name
    	boolean rn_proceed = true;
    	if(options.containsKey("-rn")){
    		rn_proceed = false;
    		if( ((java.util.List<String>)options.get("-rn")).contains(resource.getRepositoryName()) )
    			rn_proceed = true;
    	}
    	
    	// internal only
    	boolean io_proceed = true;
    	if(options.containsKey("-io")){
    		io_proceed = resource.getInternalOnly();
    	}
    	
    	
    	
    	return (fas_proceed & author_proceed & eadfauid_proceed & rid_proceed & rid1_proceed & rid2_proceed & rid3_proceed & rn_proceed & io_proceed);
    }

    
    /*
     *
     * Method below this point do not need to implemented in a command line plugin,
     * but can be for those that also have GUIs, for example for configuration.
     *
     */

    public void setApplicationFrame(ApplicationFrame mainFrame) { }

    public void showPlugin() { }

    public void showPlugin(Frame owner) { }

    public void showPlugin(Dialog owner) { }

    public HashMap getEmbeddedPanels() { return null; }

    public void setEditorField(ArchDescriptionFields editorField) { }

    public void setEditorField(DomainEditorFields domainEditorFields) { }

    public void setModel(DomainObject domainObject, InfiniteProgressPanel monitor) { }

    public void setCallingTable(JTable callingTable) { }

    public void setSelectedRow(int selectedRow) { }

    public void setRecordPositionText(int recordNumber, int totalRecords) { }

    public String getEditorType() {return null; }

    protected void doStart() { }

    protected void doStop() { }
}
