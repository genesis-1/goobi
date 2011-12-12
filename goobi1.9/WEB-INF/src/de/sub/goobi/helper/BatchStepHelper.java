package de.sub.goobi.helper;

/**
 * This file is part of the Goobi Application - a Workflow tool for the support of mass digitization.
 * 
 * Visit the websites for more information. 
 * 			- http://digiverso.com 
 * 			- http://www.intranda.com
 * 
 * Copyright 2011, intranda GmbH, Göttingen
 * 
 * 
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 * 
 * Linking this library statically or dynamically with other modules is making a combined work based on this library. Thus, the terms and conditions
 * of the GNU General Public License cover the whole combination. As a special exception, the copyright holders of this library give you permission to
 * link this library with independent modules to produce an executable, regardless of the license terms of these independent modules, and to copy and
 * distribute the resulting executable under terms of your choice, provided that you also meet, for each linked independent module, the terms and
 * conditions of the license of that module. An independent module is a module which is not derived from or based on this library. If you modify this
 * library, you may extend this exception to your version of the library, but you are not obliged to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 */
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.goobi.production.cli.helper.WikiFieldHelper;
import org.goobi.production.flow.jobs.HistoryAnalyserJob;
import org.goobi.production.properties.ProcessProperty;
import org.goobi.production.properties.PropertyParser;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

import de.sub.goobi.Beans.Benutzer;
import de.sub.goobi.Beans.HistoryEvent;
import de.sub.goobi.Beans.Prozess;
import de.sub.goobi.Beans.Prozesseigenschaft;
import de.sub.goobi.Beans.Schritt;
import de.sub.goobi.Beans.Schritteigenschaft;
import de.sub.goobi.Export.dms.ExportDms;
import de.sub.goobi.Forms.AktuelleSchritteForm;
import de.sub.goobi.Metadaten.MetadatenImagesHelper;
import de.sub.goobi.Metadaten.MetadatenVerifizierung;
import de.sub.goobi.Persistence.ProzessDAO;
import de.sub.goobi.Persistence.SchrittDAO;
import de.sub.goobi.config.ConfigMain;
import de.sub.goobi.helper.enums.HistoryEventType;
import de.sub.goobi.helper.enums.PropertyType;
import de.sub.goobi.helper.enums.StepEditType;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;

public class BatchStepHelper {

	private List<Schritt> steps;
	private ProzessDAO pdao = new ProzessDAO();
	private SchrittDAO stepDAO = new SchrittDAO();
	private static final Logger logger = Logger.getLogger(BatchStepHelper.class);
	private Schritt currentStep;
	private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private List<ProcessProperty> processPropertyList;
	private ProcessProperty processProperty;
	private List<Integer> containers = new ArrayList<Integer>();
	private Integer container;

	public BatchStepHelper(List<Schritt> steps) {
		this.steps = steps;
		for (Schritt s : steps) {

			this.processNameList.add(s.getProzess().getTitel());
		}
		this.currentStep = steps.get(0);
		this.processName = this.currentStep.getProzess().getTitel();
		loadProcessProperties(this.currentStep);
	}

	public List<Schritt> getSteps() {
		return this.steps;
	}

	public void setSteps(List<Schritt> steps) {
		this.steps = steps;
	}

	public Schritt getCurrentStep() {
		return this.currentStep;
	}

	public void setCurrentStep(Schritt currentStep) {
		this.currentStep = currentStep;
	}

	/*
	 * properties
	 */

	public ProcessProperty getProcessProperty() {
		return this.processProperty;
	}

	public void setProcessProperty(ProcessProperty processProperty) {
		this.processProperty = processProperty;
	}

	public List<ProcessProperty> getProcessProperties() {
		return this.processPropertyList;
	}

	public int getPropertyListSize() {
		return this.processPropertyList.size();
	}
	
	private List<String> processNameList = new ArrayList<String>();

	public List<String> getProcessNameList() {
		return this.processNameList;
	}

	public void setProcessNameList(List<String> processNameList) {
		this.processNameList = processNameList;
	}

	private String processName = "";

	public String getProcessName() {
		return this.processName;
	}

	public void setProcessName(String processName) {
		this.processName = processName;
		for (Schritt s : this.steps) {
			if (s.getProzess().getTitel().equals(processName)) {
				this.currentStep = s;
				loadProcessProperties(this.currentStep);
				break;
			}
		}
	}

	public void saveCurrentProperty() {
		List<ProcessProperty> ppList = getContainerProperties();
		for (ProcessProperty pp : ppList) {
			this.processProperty = pp;
			if (!this.processProperty.isValid()) {
				Helper.setFehlerMeldung("Property " + this.processProperty.getName() + " is not valid");
				return;
			}
			this.processProperty.transfer();

			Prozess p = this.currentStep.getProzess();
			List<Prozesseigenschaft> props = p.getEigenschaftenList();
			for (Prozesseigenschaft pe : props) {
				if (pe.getTitel() == null) {
					p.getEigenschaften().remove(pe);
				}
			}
			if (!this.processProperty.getProzesseigenschaft().getProzess().getEigenschaften().contains(this.processProperty.getProzesseigenschaft())) {
				this.processProperty.getProzesseigenschaft().getProzess().getEigenschaften().add(this.processProperty.getProzesseigenschaft());
			}
			try {
				this.pdao.save(this.currentStep.getProzess());
				Helper.setMeldung("Property saved");
			} catch (DAOException e) {
				logger.error(e);
				Helper.setFehlerMeldung("Properties could not be saved");
			}
		}
	}

	public void saveCurrentPropertyForAll() {
		boolean error = false;
		List<ProcessProperty> ppList = getContainerProperties();
		for (ProcessProperty pp : ppList) {
			this.processProperty = pp;
			if (!this.processProperty.isValid()) {
				Helper.setFehlerMeldung("Property " + this.processProperty.getName() + " is not valid");
				return;
			}
			this.processProperty.transfer();

			Prozesseigenschaft pe = new Prozesseigenschaft();
			pe.setTitel(this.processProperty.getName());
			pe.setWert(this.processProperty.getValue());
			pe.setContainer(this.processProperty.getContainer());

			for (Schritt s : this.steps) {
				Prozess process = s.getProzess();
				if (!s.equals(this.currentStep)) {

					if (pe.getTitel() != null) {
						boolean match = false;

						for (Prozesseigenschaft processPe : process.getEigenschaftenList()) {
							if (processPe.getTitel() != null) {
								if (pe.getTitel().equals(processPe.getTitel()) && pe.getContainer() == processPe.getContainer()) {
									processPe.setWert(pe.getWert());
									match = true;
									break;
								}
							}
						}
						if (!match) {
							Prozesseigenschaft p = new Prozesseigenschaft();
							p.setTitel(pe.getTitel());
							p.setWert(pe.getWert());
							p.setContainer(pe.getContainer());
							p.setType(pe.getType());
							p.setProzess(process);
							process.getEigenschaften().add(p);
						}
					}
				} else {
					if (!process.getEigenschaftenList().contains(this.processProperty.getProzesseigenschaft())) {
						process.getEigenschaften().add(this.processProperty.getProzesseigenschaft());
					}
				}

				List<Prozesseigenschaft> props = process.getEigenschaftenList();
				for (Prozesseigenschaft peig : props) {
					if (peig.getTitel() == null) {
						process.getEigenschaften().remove(peig);
					}
				}

				try {
					this.pdao.save(process);
				} catch (DAOException e) {
					error = true;
					logger.error(e);
					Helper.setFehlerMeldung("Properties for process " + process.getTitel() + " could not be saved");
				}
			}
		}
		if (!error) {
			Helper.setMeldung("Properties saved");
		}
	}

	private void loadProcessProperties(Schritt s) {
		this.processPropertyList = PropertyParser.getPropertiesForStep(s);
		List<Prozess> pList = new ArrayList<Prozess>();
		for (Schritt step : this.steps) {
			pList.add(step.getProzess());
		}
		for (ProcessProperty pt : this.processPropertyList) {
			if (!this.containers.contains(pt.getContainer())) {
				this.containers.add(pt.getContainer());
			}
		}
		for (Prozess p : pList) {
			for (Prozesseigenschaft pe : p.getEigenschaftenList()) {
				if (!this.containers.contains(pe.getContainer())) {
					this.containers.add(pe.getContainer());
				}

			}
		}

		Collections.sort(this.containers);
	}

	public List<Integer> getContainers() {
		return this.containers;
	}

	public int getContainersSize() {
		if (this.containers == null) {
			return 0;
		}
		return this.containers.size();
	}

	public List<ProcessProperty> getSortedProperties() {
		Comparator<ProcessProperty> comp = new ProcessProperty.CompareProperties();
		Collections.sort(this.processPropertyList, comp);
		return this.processPropertyList;
	}

	public List<ProcessProperty> getContainerlessProperties() {
		List<ProcessProperty> answer = new ArrayList<ProcessProperty>();
		for (ProcessProperty pp : this.processPropertyList) {
			if (pp.getContainer() == 0 && pp.getName() != null) {
				answer.add(pp);
			}
		}
		return answer;
	}

	public Integer getContainer() {
		return this.container;
	}

	public void setContainer(Integer container) {
		this.container = container;
		if (container != null && container > 0) {
			this.processProperty = getContainerProperties().get(0);
		}
	}

	public List<ProcessProperty> getContainerProperties() {
		List<ProcessProperty> answer = new ArrayList<ProcessProperty>();
		// int currentContainer = this.processProperty.getContainer();

		if (this.container != null && this.container > 0) {
			for (ProcessProperty pp : this.processPropertyList) {
				if (pp.getContainer() == this.container && pp.getName() != null) {
					answer.add(pp);
				}
			}
		} else {
			answer.add(this.processProperty);
		}

		return answer;
	}

	public String duplicateContainerForSingle() {
		Integer currentContainer = this.processProperty.getContainer();
		List<ProcessProperty> plist = new ArrayList<ProcessProperty>();
		// search for all properties in container
		for (ProcessProperty pt : this.processPropertyList) {
			if (pt.getContainer() == currentContainer) {
				plist.add(pt);
			}
		}
		int newContainerNumber = 0;
		if (currentContainer > 0) {
			newContainerNumber++;
			// find new unused container number
			boolean search = true;
			while (search) {
				if (!this.containers.contains(newContainerNumber)) {
					search = false;
				} else {
					newContainerNumber++;
				}
			}
		}
		// clone properties
		for (ProcessProperty pt : plist) {
			ProcessProperty newProp = pt.getClone(newContainerNumber);
			this.processPropertyList.add(newProp);
			this.processProperty = newProp;
			saveCurrentProperty();
		}
		loadProcessProperties(this.currentStep);

		return "";
	}

	private void saveStep() {
		Prozess p = this.currentStep.getProzess();
		List<Prozesseigenschaft> props = p.getEigenschaftenList();
		for (Prozesseigenschaft pe : props) {
			if (pe.getTitel() == null) {
				p.getEigenschaften().remove(pe);
			}
		}
		try {
			this.pdao.save(this.currentStep.getProzess());
		} catch (DAOException e) {
			logger.error(e);
		}
	}

	// TODO wird nur für currentStep ausgeführt
	public String duplicateContainerForAll() {
		Integer currentContainer = this.processProperty.getContainer();
		List<ProcessProperty> plist = new ArrayList<ProcessProperty>();
		// search for all properties in container
		for (ProcessProperty pt : this.processPropertyList) {
			if (pt.getContainer() == currentContainer) {
				plist.add(pt);
			}
		}

		int newContainerNumber = 0;
		if (currentContainer > 0) {
			newContainerNumber++;
			boolean search = true;
			while (search) {
				if (!this.containers.contains(newContainerNumber)) {
					search = false;
				} else {
					newContainerNumber++;
				}
			}
		}
		// clone properties
		for (ProcessProperty pt : plist) {
			ProcessProperty newProp = pt.getClone(newContainerNumber);
			this.processPropertyList.add(newProp);
			this.processProperty = newProp;
			saveCurrentPropertyForAll();
		}
		loadProcessProperties(this.currentStep);
		return "";
	}

	/*
	 * Error management
	 */

	public String ReportProblemForSingle() {

		this.myDav.UploadFromHome(this.currentStep.getProzess());
		reportProblem();
		this.problemMessage = "";
		this.myProblemID = 0;
		saveStep();
		AktuelleSchritteForm asf = (AktuelleSchritteForm) Helper.getManagedBeanValue("#{AktuelleSchritteForm}");
		return asf.FilterAlleStart();
	}

	public String ReportProblemForAll() {
		for (Schritt s : this.steps) {
			this.currentStep = s;
			this.myDav.UploadFromHome(this.currentStep.getProzess());
			reportProblem();
			saveStep();
		}
		this.problemMessage = "";
		this.myProblemID = 0;
		AktuelleSchritteForm asf = (AktuelleSchritteForm) Helper.getManagedBeanValue("#{AktuelleSchritteForm}");
		return asf.FilterAlleStart();
	}

	private void reportProblem() {
		Date myDate = new Date();
		this.currentStep.setBearbeitungsstatusEnum(StepStatus.LOCKED);
		this.currentStep.setEditTypeEnum(StepEditType.MANUAL_SINGLE);
		HelperSchritte.updateEditing(this.currentStep);
		this.currentStep.setBearbeitungsbeginn(null);

		try {

			Schritt temp = this.stepDAO.get(this.myProblemID);
			temp.setBearbeitungsstatusEnum(StepStatus.OPEN);
			// if (temp.getPrioritaet().intValue() == 0)
			temp.setCorrectionStep();
			temp.setBearbeitungsende(null);
			Schritteigenschaft se = new Schritteigenschaft();
			Benutzer ben = (Benutzer) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");

			se.setTitel(Helper.getTranslation("Korrektur notwendig"));
			se.setWert("[" + this.formatter.format(new Date()) + ", " + ben.getNachVorname() + "] " + this.problemMessage);
			se.setType(PropertyType.messageError);
			se.setCreationDate(myDate);
			se.setSchritt(temp);
			temp.getEigenschaften().add(se);
			this.stepDAO.save(temp);
			this.currentStep
					.getProzess()
					.getHistory()
					.add(new HistoryEvent(myDate, temp.getReihenfolge().doubleValue(), temp.getTitel(), HistoryEventType.stepError, temp.getProzess()));
			/*
			 * alle Schritte zwischen dem aktuellen und dem Korrekturschritt wieder schliessen
			 */
			@SuppressWarnings("unchecked")
			List<Schritt> alleSchritteDazwischen = Helper.getHibernateSession().createCriteria(Schritt.class)
					.add(Restrictions.le("reihenfolge", this.currentStep.getReihenfolge()))
					.add(Restrictions.gt("reihenfolge", temp.getReihenfolge())).addOrder(Order.asc("reihenfolge")).createCriteria("prozess")
					.add(Restrictions.idEq(this.currentStep.getProzess().getId())).list();
			for (Iterator<Schritt> iter = alleSchritteDazwischen.iterator(); iter.hasNext();) {
				Schritt step = iter.next();
				step.setBearbeitungsstatusEnum(StepStatus.LOCKED);
				// if (step.getPrioritaet().intValue() == 0)
				step.setCorrectionStep();
				step.setBearbeitungsende(null);
				Schritteigenschaft seg = new Schritteigenschaft();
				seg.setTitel(Helper.getTranslation("Korrektur notwendig"));
				seg.setWert(Helper.getTranslation("KorrekturFuer") + temp.getTitel() + ": " + this.problemMessage);
				seg.setSchritt(step);
				seg.setType(PropertyType.messageImportant);
				seg.setCreationDate(new Date());
				step.getEigenschaften().add(seg);
				// this.stepDAO.save(step);
			}

			/*
			 * den Prozess aktualisieren, so dass der Sortierungshelper gespeichert wird
			 */
			// this.pdao.save(this.currentStep.getProzess());
		} catch (DAOException e) {
		}
	}

	@SuppressWarnings("unchecked")
	public List<Schritt> getPreviousStepsForProblemReporting() {
		List<Schritt> alleVorherigenSchritte = Helper.getHibernateSession().createCriteria(Schritt.class)
				.add(Restrictions.lt("reihenfolge", this.currentStep.getReihenfolge())).addOrder(Order.asc("reihenfolge")).createCriteria("prozess")
				.add(Restrictions.idEq(this.currentStep.getProzess().getId())).list();

		return alleVorherigenSchritte;
	}

	@SuppressWarnings("unchecked")
	public List<Schritt> getNextStepsForProblemSolution() {
		List<Schritt> alleNachfolgendenSchritte = Helper.getHibernateSession().createCriteria(Schritt.class)
				.add(Restrictions.ge("reihenfolge", this.currentStep.getReihenfolge())).add(Restrictions.eq("prioritaet", 10))
				.addOrder(Order.asc("reihenfolge")).createCriteria("prozess").add(Restrictions.idEq(this.currentStep.getProzess().getId())).list();
		return alleNachfolgendenSchritte;
	}

	public String SolveProblemForSingle() {
		for (Schritt s : this.steps) {
			this.currentStep = s;
			solveProblem();
		}
		this.solutionMessage = "";
		this.mySolutionID = 0;

		AktuelleSchritteForm asf = (AktuelleSchritteForm) Helper.getManagedBeanValue("#{AktuelleSchritteForm}");
		return asf.FilterAlleStart();
	}

	public String SolveProblemForAll() {
		solveProblem();
		this.solutionMessage = "";
		this.mySolutionID = 0;

		AktuelleSchritteForm asf = (AktuelleSchritteForm) Helper.getManagedBeanValue("#{AktuelleSchritteForm}");
		return asf.FilterAlleStart();
	}

	private void solveProblem() {
		Date now = new Date();
		this.myDav.UploadFromHome(this.currentStep.getProzess());
		this.currentStep.setBearbeitungsstatusEnum(StepStatus.DONE);
		this.currentStep.setBearbeitungsende(now);
		this.currentStep.setEditTypeEnum(StepEditType.MANUAL_SINGLE);
		HelperSchritte.updateEditing(this.currentStep);

		try {
			Schritt temp = this.stepDAO.get(this.mySolutionID);

			/*
			 * alle Schritte zwischen dem aktuellen und dem Korrekturschritt wieder schliessen
			 */
			@SuppressWarnings("unchecked")
			List<Schritt> alleSchritteDazwischen = Helper.getHibernateSession().createCriteria(Schritt.class)
					.add(Restrictions.ge("reihenfolge", this.currentStep.getReihenfolge()))
					.add(Restrictions.le("reihenfolge", temp.getReihenfolge())).addOrder(Order.asc("reihenfolge")).createCriteria("prozess")
					.add(Restrictions.idEq(this.currentStep.getProzess().getId())).list();
			for (Iterator<Schritt> iter = alleSchritteDazwischen.iterator(); iter.hasNext();) {
				Schritt step = iter.next();
				step.setBearbeitungsstatusEnum(StepStatus.DONE);
				step.setBearbeitungsende(now);
				step.setPrioritaet(Integer.valueOf(0));
				if (step.getId().intValue() == temp.getId().intValue()) {
					step.setBearbeitungsstatusEnum(StepStatus.OPEN);
					step.setCorrectionStep();
					step.setBearbeitungsende(null);
					// step.setBearbeitungsbeginn(null);
					step.setBearbeitungszeitpunkt(now);
				}
				Schritteigenschaft seg = new Schritteigenschaft();
				seg.setTitel(Helper.getTranslation("Korrektur durchgefuehrt"));
				Benutzer ben = (Benutzer) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
				seg.setWert("[" + this.formatter.format(new Date()) + ", " + ben.getNachVorname() + "] "
						+ Helper.getTranslation("KorrekturloesungFuer") + " " + temp.getTitel() + ": " + this.solutionMessage);
				seg.setSchritt(step);
				seg.setType(PropertyType.messageImportant);
				seg.setCreationDate(new Date());
				step.getEigenschaften().add(seg);
				this.stepDAO.save(step);
			}

			/*
			 * den Prozess aktualisieren, so dass der Sortierungshelper gespeichert wird
			 */
			// this.pdao.save(this.currentStep.getProzess());
		} catch (DAOException e) {
		}
	}

	private Integer myProblemID;
	private Integer mySolutionID;
	private String problemMessage;
	private String solutionMessage;

	public String getProblemMessage() {
		return this.problemMessage;
	}

	public void setProblemMessage(String problemMessage) {
		this.problemMessage = problemMessage;
	}

	public Integer getMyProblemID() {
		return this.myProblemID;
	}

	public void setMyProblemID(Integer myProblemID) {
		this.myProblemID = myProblemID;
	}

	public String getSolutionMessage() {
		return this.solutionMessage;
	}

	public void setSolutionMessage(String solutionMessage) {
		this.solutionMessage = solutionMessage;
	}

	public Integer getMySolutionID() {
		return this.mySolutionID;
	}

	public void setMySolutionID(Integer mySolutionID) {
		this.mySolutionID = mySolutionID;
	}

	/**
	 * sets new value for wiki field
	 * 
	 * @param inString
	 */

	private String addToWikiField = "";

	public void setWikiField(String inString) {
		this.currentStep.getProzess().setWikifield(inString);
	}

	public String getWikiField() {
		return this.currentStep.getProzess().getWikifield();

	}

	public String getAddToWikiField() {
		return this.addToWikiField;
	}

	public void setAddToWikiField(String addToWikiField) {
		this.addToWikiField = addToWikiField;
	}

	public void addToWikiField() {
		Benutzer user = (Benutzer) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
		String message = this.addToWikiField + " (" + user.getNachVorname() + ")";
		this.currentStep.getProzess().setWikifield(WikiFieldHelper.getWikiMessage(this.currentStep.getProzess().getWikifield(), "user", message));
		this.addToWikiField = "";
		try {
			this.pdao.save(this.currentStep.getProzess());
		} catch (DAOException e) {
			logger.error(e);
		}
	}

	public void addToWikiFieldForAll() {
		Benutzer user = (Benutzer) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
		String message = this.addToWikiField + " (" + user.getNachVorname() + ")";
		for (Schritt s : this.steps) {
			s.getProzess().setWikifield(WikiFieldHelper.getWikiMessage(s.getProzess().getWikifield(), "user", message));
			try {
				this.pdao.save(s.getProzess());
			} catch (DAOException e) {
				logger.error(e);
			}
		}
		this.addToWikiField = "";
	}

	/*
	 * actions
	 */

	private String script;
	private WebDav myDav = new WebDav();

	public String getScript() {
		return this.script;
	}

	public void setScript(String script) {
		this.script = script;
	}

	public void executeScript() {
		for (Schritt step : this.steps) {

			if (step.getAllScripts().containsKey(this.script)) {

				String scriptPath = step.getAllScripts().get(this.script);
				try {
					new HelperSchritte().executeScript(step, scriptPath, false);
				} catch (SwapException e) {
					logger.error(e);
				}
			}
		}

	}

	public void ExportDMS() {
		for (Schritt step : this.steps) {
			ExportDms export = new ExportDms();
			try {
				export.startExport(step.getProzess());
			} catch (Exception e) {
				Helper.setFehlerMeldung("Error on export", e.getMessage());
				logger.error(e);
			}
		}
	}

	public String BatchDurchBenutzerZurueckgeben() {

		for (Schritt s : this.steps) {

			this.myDav.UploadFromHome(s.getProzess());
			s.setBearbeitungsstatusEnum(StepStatus.OPEN);
			if (s.isCorrectionStep()) {
				s.setBearbeitungsbeginn(null);
			}
			s.setEditTypeEnum(StepEditType.MANUAL_MULTI);
			HelperSchritte.updateEditing(s);

			try {
				this.pdao.save(s.getProzess());
			} catch (DAOException e) {
			}
		}
		AktuelleSchritteForm asf = (AktuelleSchritteForm) Helper.getManagedBeanValue("#{AktuelleSchritteForm}");
		return asf.FilterAlleStart();
	}

	public String BatchDurchBenutzerAbschliessen() {
		for (ProcessProperty pp : this.processPropertyList) {
			this.processProperty = pp;
			saveCurrentPropertyForAll();
		}
		for (Schritt s : this.steps) {

			if (s.isTypImagesSchreiben()) {
				try {
					s.getProzess().setSortHelperImages(FileUtils.getNumberOfFiles(new File(s.getProzess().getImagesOrigDirectory())));
					HistoryAnalyserJob.updateHistory(s.getProzess());
				} catch (Exception e) {
					Helper.setFehlerMeldung("Error while calculation of storage and images", e);
				}
			}

			if (s.isTypBeimAbschliessenVerifizieren()) {
				if (s.isTypMetadaten() && ConfigMain.getBooleanParameter("useMetadatenvalidierung")) {
					MetadatenVerifizierung mv = new MetadatenVerifizierung();
					mv.setAutoSave(true);
					if (!mv.validate(s.getProzess())) {
						return "";
					}
				}
				if (s.isTypImagesSchreiben()) {
					MetadatenImagesHelper mih = new MetadatenImagesHelper(null, null);
					try {
						if (!mih.checkIfImagesValid(s.getProzess(), s.getProzess().getImagesOrigDirectory())) {
							return "";
						}
					} catch (Exception e) {
						Helper.setFehlerMeldung("Error on image validation: ", e);
					}
				}
			}

			this.myDav.UploadFromHome(s.getProzess());
			s.setEditTypeEnum(StepEditType.MANUAL_MULTI);
			new HelperSchritte().SchrittAbschliessen(s, false);
		}
		Helper.createNewHibernateSession();
		AktuelleSchritteForm asf = (AktuelleSchritteForm) Helper.getManagedBeanValue("#{AktuelleSchritteForm}");
		return asf.FilterAlleStart();
	}

	public List<String> getScriptnames() {
		List<String> answer = new ArrayList<String>();
		answer.addAll(getCurrentStep().getAllScripts().keySet());
		return answer;
	}
}
