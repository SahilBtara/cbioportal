/*
 * Copyright (c) 2015 Memorial Sloan-Kettering Cancer Center.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF MERCHANTABILITY OR FITNESS
 * FOR A PARTICULAR PURPOSE. The software and documentation provided hereunder
 * is on an "as is" basis, and Memorial Sloan-Kettering Cancer Center has no
 * obligations to provide maintenance, support, updates, enhancements or
 * modifications. In no event shall Memorial Sloan-Kettering Cancer Center be
 * liable to any party for direct, indirect, special, incidental or
 * consequential damages, including lost profits, arising out of the use of this
 * software and its documentation, even if Memorial Sloan-Kettering Cancer
 * Center has been advised of the possibility of such damage.
 */

/*
 * This file is part of cBioPortal.
 *
 * cBioPortal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.mskcc.cbio.portal.scripts;

import org.mskcc.cbio.portal.dao.*;
import org.mskcc.cbio.portal.model.*;
import org.mskcc.cbio.portal.util.*;

import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Code to Import Copy Number Alteration, MRNA Expression Data, or protein RPPA data
 *
 * @author Ethan Cerami
 */
public class ImportTabDelimData {
    private HashSet<Long> importedGeneSet = new HashSet<Long>();
    private static Logger logger = Logger.getLogger(ImportTabDelimData.class);

    private File mutationFile;
    private String targetLine;
    private int geneticProfileId;
    private GeneticProfile geneticProfile;

    /**
     * Constructor.
     *
     * @param dataFile         Data File containing Copy Number Alteration, MRNA Expression Data, or protein RPPA data
     * @param targetLine       The line we want to import.
     *                         If null, all lines are imported.
     * @param geneticProfileId GeneticProfile ID.
     * 
     * @deprecated : TODO shall we deprecate this feature (i.e. the targetLine)? 
     */
    public ImportTabDelimData(File dataFile, String targetLine, int geneticProfileId) {
        this.mutationFile = dataFile;
        this.targetLine = targetLine;
        this.geneticProfileId = geneticProfileId;
    }

    /**
     * Constructor.
     *
     * @param dataFile         Data File containing Copy Number Alteration, MRNA Expression Data, or protein RPPA data
     * @param geneticProfileId GeneticProfile ID.
     */
    public ImportTabDelimData(File dataFile, int geneticProfileId) {
        this.mutationFile = dataFile;
        this.geneticProfileId = geneticProfileId;
    }

    /**
     * Import the Copy Number Alteration, MRNA Expression Data, or protein RPPA data
     *
     * @throws IOException  IO Error.
     * @throws DaoException Database Error.
     */
    public void importData(int numLines) throws IOException, DaoException {

        geneticProfile = DaoGeneticProfile.getGeneticProfileById(geneticProfileId);

        FileReader reader = new FileReader(mutationFile);
        BufferedReader buf = new BufferedReader(reader);
        String headerLine = buf.readLine();
        String parts[] = headerLine.split("\t");

        int numRecordsToAdd = 0;
        try {
	        //Whether data regards CNA or RPPA:
	        boolean discritizedCnaProfile = geneticProfile!=null
	                                        && geneticProfile.getGeneticAlterationType() == GeneticAlterationType.COPY_NUMBER_ALTERATION
	                                        && geneticProfile.showProfileInAnalysisTab();
	        boolean rppaProfile = geneticProfile!=null
	                                && geneticProfile.getGeneticAlterationType() == GeneticAlterationType.PROTEIN_LEVEL
	                                && "Composite.Element.Ref".equalsIgnoreCase(parts[0]);
        	
        	int hugoSymbolIndex = getHugoSymbolIndex(parts);
	        int entrezGeneIdIndex = getEntrezGeneIdIndex(parts);
	        int rppaGeneRefIndex = getRppaGeneRefIndex(parts);
	        int sampleStartIndex = getStartIndex(parts, hugoSymbolIndex, entrezGeneIdIndex, rppaGeneRefIndex);
	        if (rppaProfile) {
	        	if (rppaGeneRefIndex == -1)
	        		throw new RuntimeException("Error: the following column should be present for RPPA data: Composite.Element.Ref");
	        }	
	        else if (hugoSymbolIndex == -1 && entrezGeneIdIndex == -1)
	        	throw new RuntimeException("Error: at least one of the following columns should be present: Hugo_Symbol or Entrez_Gene_Id");
	        
	        String sampleIds[];
	        //  Branch, depending on targetLine setting
	        if (targetLine == null) {
	            sampleIds = new String[parts.length - sampleStartIndex];
	            System.arraycopy(parts, sampleStartIndex, sampleIds, 0, parts.length - sampleStartIndex);
	        } else {
	            sampleIds = new String[parts.length - sampleStartIndex];
	            System.arraycopy(parts, sampleStartIndex, sampleIds, 0, parts.length - sampleStartIndex);
	        }
	        //TODO - lines below should be removed. Agreed with JJ to remove this as soon as MSK moves to new validation 
	        //procedure. In this new procedure, Patients and Samples should only be added 
	        //via the corresponding ImportClinicalData process. Furthermore, the code below is wrong as it assumes one 
	        //sample per patient, which is not always the case.
	        ImportDataUtil.addPatients(sampleIds, geneticProfileId);
	        int nrUnknownSamplesAdded = ImportDataUtil.addSamples(sampleIds, geneticProfileId);
	        if (nrUnknownSamplesAdded > 0) {
	        	ProgressMonitor.logWarning("WARNING: Number of samples added on the fly because they were missing in clinical data:  " + nrUnknownSamplesAdded);
	        }
	        
	        ProgressMonitor.setCurrentMessage(" --> total number of samples: " + sampleIds.length);
	        ProgressMonitor.setCurrentMessage(" --> total number of data lines:  " + (numLines-1));
	
	        // link Samples to the genetic profile
	        ArrayList <Integer> orderedSampleList = new ArrayList<Integer>();
	        ArrayList <Integer> filteredSampleIndices = new ArrayList<Integer>();
	        for (int i = 0; i < sampleIds.length; i++) {
	           Sample sample = DaoSample.getSampleByCancerStudyAndSampleId(geneticProfile.getCancerStudyId(),
	                                                                       StableIdUtil.getSampleId(sampleIds[i]));
	           if (sample == null) {
	                assert StableIdUtil.isNormal(sampleIds[i]);
	                filteredSampleIndices.add(i);
	                continue;
	           }
	           if (!DaoSampleProfile.sampleExistsInGeneticProfile(sample.getInternalId(), geneticProfileId)) {
	               DaoSampleProfile.addSampleProfile(sample.getInternalId(), geneticProfileId);
	           }
	           orderedSampleList.add(sample.getInternalId());
	        }
	        DaoGeneticProfileSamples.addGeneticProfileSamples(geneticProfileId, orderedSampleList);
	
	        //Gene cache:
	        DaoGeneOptimized daoGene = DaoGeneOptimized.getInstance();
	
	        //Object to insert records in the generic 'genetic_alteration' table: 
	        DaoGeneticAlteration daoGeneticAlteration = DaoGeneticAlteration.getInstance();
	        
	        //cache for data found in  cna_event' table:
	        Map<CnaEvent.Event, CnaEvent.Event> existingCnaEvents = null;	        
	        if (discritizedCnaProfile) {
	            existingCnaEvents = new HashMap<CnaEvent.Event, CnaEvent.Event>();
	            for (CnaEvent.Event event : DaoCnaEvent.getAllCnaEvents()) {
	                existingCnaEvents.put(event, event);
	            }
	            MySQLbulkLoader.bulkLoadOn();
	        }
	        
	        int lenParts = parts.length;
	        
	        String line = buf.readLine();
	        while (line != null) {
	            ProgressMonitor.incrementCurValue();
	            ConsoleUtil.showProgress();
	        	if (parseLine(line, lenParts, sampleStartIndex, 
	        			hugoSymbolIndex, entrezGeneIdIndex, rppaGeneRefIndex,
	        			rppaProfile, discritizedCnaProfile, 
	        			daoGene, 
	        			filteredSampleIndices, orderedSampleList, 
	        			existingCnaEvents, daoGeneticAlteration))
	        		numRecordsToAdd++;
	            line = buf.readLine();
	        }
	        if (MySQLbulkLoader.isBulkLoad()) {
	           MySQLbulkLoader.flushAll();
	        }
        }
        catch (Exception e) {
        	System.err.println(e.getMessage());
        }
        finally {
	        buf.close(); 
	        if (numRecordsToAdd == 0) {
	            throw new DaoException ("Something has gone wrong!  I did not save any records" +
	                    " to the database!");
	        }
        }
        
    }
    
    private boolean parseLine(String line, int nrColumns, int sampleStartIndex, 
    		int hugoSymbolIndex, int entrezGeneIdIndex, int rppaGeneRefIndex,
    		boolean rppaProfile, boolean discritizedCnaProfile,
    		DaoGeneOptimized daoGene,
    		List <Integer> filteredSampleIndices, List <Integer> orderedSampleList,
    		Map<CnaEvent.Event, CnaEvent.Event> existingCnaEvents, DaoGeneticAlteration daoGeneticAlteration
    		) throws DaoException {
        
    	boolean recordStored = false; 
    	
        //  Ignore lines starting with #
        if (!line.startsWith("#") && line.trim().length() > 0) {
            String[] parts = line.split("\t",-1);
            
            if (parts.length>nrColumns) {
                if (line.split("\t").length>nrColumns) {
                    System.err.println("The following line has more fields (" + parts.length
                            + ") than the headers(" + nrColumns + "): \n"+parts[0]);
                }
            }
            String values[] = (String[]) ArrayUtils.subarray(parts, sampleStartIndex, parts.length>nrColumns?nrColumns:parts.length);
            values = filterOutNormalValues(filteredSampleIndices, values);

            String geneSymbol = null;
            if (hugoSymbolIndex != -1) {
            	geneSymbol = parts[hugoSymbolIndex];
            }
            //RPPA:
            if (rppaGeneRefIndex != -1) {
            	geneSymbol = parts[rppaGeneRefIndex];
            }
            if (geneSymbol!=null && geneSymbol.isEmpty()) {
                geneSymbol = null;
            }
            
            String entrez = null;
            if (entrezGeneIdIndex!=-1) {
                entrez = parts[entrezGeneIdIndex];
            }
            if (entrez!=null) {
            	if (entrez.isEmpty()) {
            		entrez = null;
            	}
            	else if (!entrez.matches("-?[0-9]+")) {
            		ProgressMonitor.logWarning("Ignoring line with invalid Entrez_Id " + entrez);
                	return false;
            	}            	
            }
            
            //If all are empty, skip line:
            if (geneSymbol == null && entrez == null) {
            	ProgressMonitor.logWarning("Ignoring line with no Hugo_Symbol or Entrez_Id " + (rppaProfile? "or Composite.Element.REF ":"") + " value");
            	return false;
            }
            else {
                if (geneSymbol != null && (geneSymbol.contains("///") || geneSymbol.contains("---"))) {
                    //  Ignore gene IDs separated by ///.  This indicates that
                    //  the line contains information regarding multiple genes, and
                    //  we cannot currently handle this.
                    //  Also, ignore gene IDs that are specified as ---.  This indicates
                    //  the line contains information regarding an unknown gene, and
                    //  we cannot currently handle this.
                    ProgressMonitor.logWarning("Ignoring gene ID:  " + geneSymbol);
                    return false;
                } else {
                	List<CanonicalGene> genes = null;
                	//If rppa, parse genes from "Composite.Element.REF" column:
                	if (rppaProfile) {
                        genes = parseRPPAGenes(geneSymbol);
                    }
                	else {
	                	//try entrez:
	                    if (entrez!=null) {
	                        CanonicalGene gene = daoGene.getGene(Long.parseLong(entrez));
	                        if (gene!=null) {
	                            genes = Arrays.asList(gene);
	                        }
	                        else {
	                        	ProgressMonitor.logWarning("Entrez_Id " + entrez + " not found. Record will be skipped for this gene.");
	                        	return false;
	                        }
	                    } 
	                    //no entrez, try hugo:
	                    if (genes==null && geneSymbol != null) {
	                        // deal with multiple symbols separate by |, use the first one
	                        int ix = geneSymbol.indexOf("|");
	                        if (ix>0) {
	                            geneSymbol = geneSymbol.substring(0, ix);
	                        }
	
	                        genes = daoGene.getGene(geneSymbol, true);
	                    }
                	}

                    if (genes == null || genes.isEmpty()) {
                        genes = Collections.emptyList();
                    }

                    //  If no target line is specified or we match the target, process.
                    if (targetLine == null || parts[0].equals(targetLine)) {
                        if (genes.isEmpty()) {
                            //  if gene is null, we might be dealing with a micro RNA ID
                            if (geneSymbol != null && geneSymbol.toLowerCase().contains("-mir-")) {
//                                if (microRnaIdSet.contains(geneId)) {
//                                    storeMicroRnaAlterations(values, daoMicroRnaAlteration, geneId);
//                                    numRecordsStored++;
//                                } else {
                                    ProgressMonitor.logWarning("microRNA is not known to me:  [" + geneSymbol
                                        + "]. Ignoring it "
                                        + "and all tab-delimited data associated with it!");
//                                }
                            } else {
                                String gene = (geneSymbol != null) ? geneSymbol : entrez;
                                ProgressMonitor.logWarning("Gene not found:  [" + gene
                                    + "]. Ignoring it "
                                    + "and all tab-delimited data associated with it!");
                            }
                        } else if (genes.size()==1) {
                            if (discritizedCnaProfile) {
                                long entrezGeneId = genes.get(0).getEntrezGeneId();
                                int n = values.length;
                                if (n==0)
                                    System.out.println();
                                int i = values[0].equals(""+entrezGeneId) ? 1:0;
                                for (; i<n; i++) {
                                    
                                    // temporary solution -- change partial deletion back to full deletion.
                                    if (values[i].equals(GeneticAlterationType.PARTIAL_DELETION)) {
                                        values[i] = GeneticAlterationType.HOMOZYGOUS_DELETION;
                                    }
                                    
                                    if (values[i].equals(GeneticAlterationType.AMPLIFICATION) 
                                           // || values[i].equals(GeneticAlterationType.GAIN)  >> skipping GAIN, ZERO, HEMIZYGOUS_DELETION to minimize size of dataset in DB
                                           // || values[i].equals(GeneticAlterationType.ZERO)
                                           // || values[i].equals(GeneticAlterationType.HEMIZYGOUS_DELETION)
                                            || values[i].equals(GeneticAlterationType.HOMOZYGOUS_DELETION)) {
                                        CnaEvent cnaEvent = new CnaEvent(orderedSampleList.get(i), geneticProfileId, entrezGeneId, Short.parseShort(values[i]));
                                        
                                        if (existingCnaEvents.containsKey(cnaEvent.getEvent())) {
                                            cnaEvent.setEventId(existingCnaEvents.get(cnaEvent.getEvent()).getEventId());
                                            DaoCnaEvent.addCaseCnaEvent(cnaEvent, false);
                                        } else {
                                        	//cnaEvent.setEventId(++cnaEventId); not needed anymore, column now has AUTO_INCREMENT 
                                            DaoCnaEvent.addCaseCnaEvent(cnaEvent, true);
                                            existingCnaEvents.put(cnaEvent.getEvent(), cnaEvent.getEvent());
                                        }
                                    }
                                }
                            }
                            recordStored = storeGeneticAlterations(values, daoGeneticAlteration, genes.get(0), geneSymbol);
                        } else {
                        	//TODO - review: is this still correct? 
                            for (CanonicalGene gene : genes) {
                                if (gene.isMicroRNA() || rppaProfile) { // for micro rna or protein data, duplicate the data
                                	recordStored = storeGeneticAlterations(values, daoGeneticAlteration, gene, geneSymbol);
                                }
                            }
                            if (!recordStored) {
                            	//this means that genes.size() > 1 and data was not rppa or microRNA, so it is not defined how to deal with 
                            	//the ambiguous alias list. Report this:
                            	ProgressMonitor.logWarning("Gene symbol " + geneSymbol + " found to be ambiguous. Record will be skipped for this gene.");
                            }
                        }
                    }
                }
            }
        }
        return recordStored;
	}

	private boolean storeGeneticAlterations(String[] values, DaoGeneticAlteration daoGeneticAlteration,
            CanonicalGene gene, String geneSymbol) throws DaoException {
		//  Check that we have not already imported information regarding this gene.
        //  This is an important check, because a GISTIC or RAE file may contain
        //  multiple rows for the same gene, and we only want to import the first row.
        if (!importedGeneSet.contains(gene.getEntrezGeneId())) {
            daoGeneticAlteration.addGeneticAlterations(geneticProfileId, gene.getEntrezGeneId(), values);
            importedGeneSet.add(gene.getEntrezGeneId());
            return true;
        }
        else {
        	//TODO - review this part - maybe it should be an Exception instead of just a warning.
        	String geneSymbolMessage = "";
        	if (geneSymbol != null)
        		geneSymbolMessage = "(given in your file as: " + geneSymbol + ") ";
        	ProgressMonitor.logWarning("Gene " + gene.getHugoGeneSymbolAllCaps() + " (" + gene.getEntrezGeneId() + ")" + geneSymbolMessage + " found to be duplicated in your file. Duplicated row will be ignored!");
        	return false;
        }
    }
    
    private List<CanonicalGene> parseRPPAGenes(String antibodyWithGene) throws DaoException {
        DaoGeneOptimized daoGene = DaoGeneOptimized.getInstance();
        String[] parts = antibodyWithGene.split("\\|");
        String[] symbols = parts[0].split(" ");
        String arrayId = parts[1];
        
        List<CanonicalGene> genes = new ArrayList<CanonicalGene>();
        for (String symbol : symbols) {
            CanonicalGene gene = daoGene.getNonAmbiguousGene(symbol, null);
            if (gene!=null) {
                genes.add(gene);
            }
            else {
            	ProgressMonitor.logWarning("Gene " + symbol + " not found in DB. Record will be skipped for this gene.");
            }
        }
        
        Pattern p = Pattern.compile("(p[STY][0-9]+)");
        Matcher m = p.matcher(arrayId);
        String residue;
        if (!m.find()) {
            //type is "protein_level":
            return genes;
        } else {
            //type is "phosphorylation":
            residue = m.group(1);
            return importPhosphoGene(genes, residue);
        }
    }
    
    private List<CanonicalGene> importPhosphoGene(List<CanonicalGene> genes, String residue) throws DaoException {
        DaoGeneOptimized daoGene = DaoGeneOptimized.getInstance();
        List<CanonicalGene> phosphoGenes = new ArrayList<CanonicalGene>();
        for (CanonicalGene gene : genes) {
            Set<String> aliases = new HashSet<String>();
            aliases.add("rppa-phospho");
            aliases.add("phosphoprotein");
            aliases.add("phospho"+gene.getStandardSymbol());
            String phosphoSymbol = gene.getStandardSymbol()+"_"+residue;
            CanonicalGene phosphoGene = daoGene.getGene(phosphoSymbol);
            if (phosphoGene==null) {
                phosphoGene = new CanonicalGene(phosphoSymbol, aliases);
                phosphoGene.setType(CanonicalGene.PHOSPHOPROTEIN_TYPE);
                phosphoGene.setCytoband(gene.getCytoband());
                daoGene.addGene(phosphoGene);
            }
            phosphoGenes.add(phosphoGene);
        }
        return phosphoGenes;
    }
    
    private int getHugoSymbolIndex(String[] headers) {
    	for (int i = 0; i<headers.length; i++) {
            if (headers[i].equalsIgnoreCase("Hugo_Symbol")) {
                return i;
            }
        }
        return -1;
    }
    
    private int getEntrezGeneIdIndex(String[] headers) {
        for (int i = 0; i<headers.length; i++) {
            if (headers[i].equalsIgnoreCase("Entrez_Gene_Id")) {
                return i;
            }
        }
        return -1;
    }

    private int getRppaGeneRefIndex(String[] headers) {
        for (int i = 0; i<headers.length; i++) {
            if (headers[i].equalsIgnoreCase("Composite.Element.Ref")) {
                return i;
            }
        }
        return -1;
    }
    
    private int getStartIndex(String[] headers, int hugoSymbolIndex, int entrezGeneIdIndex, int rppaGeneRefIndex) {
        int startIndex = -1;
        
        for (int i=0; i<headers.length; i++) {
            String h = headers[i];
            //if the column is not one of the gene symbol/gene ide columns or other pre-sample columns:
            if (!h.equalsIgnoreCase("Gene Symbol") &&
                    !h.equalsIgnoreCase("Hugo_Symbol") &&
                    !h.equalsIgnoreCase("Entrez_Gene_Id") &&
                    !h.equalsIgnoreCase("Locus ID") &&
                    !h.equalsIgnoreCase("Cytoband") &&
                    !h.equalsIgnoreCase("Composite.Element.Ref")) {
            	//and the column is found after  hugoSymbolIndex and entrezGeneIdIndex: 
            	if (i > hugoSymbolIndex && i > entrezGeneIdIndex && i > rppaGeneRefIndex) {
            		//then we consider this the start of the sample columns:
                	startIndex = i;
                	break;
            	}
            }
        }
        if (startIndex == -1)
        	throw new RuntimeException("Could not find a sample column in the file");
        
        return startIndex;
    }

    private String[] filterOutNormalValues(List <Integer> filteredSampleIndices, String[] values)
    {
        ArrayList<String> filteredValues = new ArrayList<String>();
        for (int lc = 0; lc < values.length; lc++) {
            if (!filteredSampleIndices.contains(lc)) {
                filteredValues.add(values[lc]);
            }
        }
        return filteredValues.toArray(new String[filteredValues.size()]);
    }
}
