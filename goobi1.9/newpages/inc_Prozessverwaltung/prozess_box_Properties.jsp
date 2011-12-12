<%@ page session="false" contentType="text/html;charset=utf-8"%>
<%@ taglib uri="http://java.sun.com/jsf/core" prefix="f"%>
<%@ taglib uri="http://java.sun.com/jsf/html" prefix="h"%>
<%@ taglib uri="http://jsftutorials.net/htmLib" prefix="htm"%>
<%@ taglib uri="http://myfaces.apache.org/tomahawk" prefix="x"%>
<%@ taglib uri="http://sourceforge.net/projects/jsf-comp/easysi" prefix="si"%>
<%@ taglib uri="http://richfaces.org/rich" prefix="rich"%>
<%@ taglib uri="https://ajax4jsf.dev.java.net/ajax" prefix="a4j"%>


<%-- ++++++++++++++++++++++++++++++++++++++++++++++++++++ --%>
<%-- ++++++++++++++++     Eigenschaftentabelle      ++++++++++++++++ --%>
<%-- ++++++++++++++++++++++++++++++++++++++++++++++++++++ --%>

<htm:h4 style="margin-top:15" rendered="#{ProzessverwaltungForm.modusBearbeiten!='eigenschaft'}">
	<h:outputText value="#{msgs.eigenschaften}" />
</htm:h4>
<htm:table cellspacing="1px" cellpadding="1px" width="100%" styleClass="standardTable"
	rendered="#{ProzessverwaltungForm.modusBearbeiten!='eigenschaft'}">

	<htm:thead styleClass="standardTable_Header">
		<htm:th>
			<h:outputText value="#{msgs.titel}" />
		</htm:th>
		<htm:th>
			<h:outputText value="#{msgs.wert}" />
		</htm:th>
		<htm:th>
			<h:outputText value="#{msgs.auswahl}" />
		</htm:th>
	</htm:thead>

		<x:dataList var="container" value="#{ProzessverwaltungForm.containers}" rowCountVar="rowCount" rowIndexVar="rowIndex">
			<x:dataList var="proc" value="#{ProzessverwaltungForm.containerlessProperties}" rowCountVar="propCount" rowIndexVar="propInd">
				<htm:tr rendered="#{container == 0}" styleClass="standardTable_Row1">
					<htm:td>
						<%-- property title --%>
						<h:outputText value="#{proc.name}" />
					</htm:td>
					<htm:td>
						<%-- property value--%>
						<h:outputText value="#{proc.value}" />
					</htm:td>
					<htm:td styleClass="standardTable_ColumnCentered">
						<%-- property action: Edit --%>
						<h:commandLink action="ProzessverwaltungBearbeiten" title="#{msgs.bearbeiten}">
							<h:graphicImage value="/newpages/images/buttons/edit.gif" />
							<x:updateActionListener property="#{ProzessverwaltungForm.processProperty}" value="#{proc}" />
							<x:updateActionListener property="#{ProzessverwaltungForm.modusBearbeiten}" value="eigenschaft" />
							<%-- <a4j:support event="onchange" reRender="editBatch" />--%>
						</h:commandLink>
						<%-- property action: Duplicate --%>
						<h:commandLink action="#{ProzessverwaltungForm.duplicateContainer}" title="#{msgs.duplicate}">
							<h:graphicImage value="/newpages/images/buttons/copy.gif" />
							<x:updateActionListener property="#{ProzessverwaltungForm.processProperty}" value="#{proc}" />
						</h:commandLink>
					</htm:td>
				</htm:tr>
			</x:dataList>

			<x:dataList var="process_item" value="#{ProzessverwaltungForm.sortedProperties}" rowCountVar="propCount" rowIndexVar="propInd">
				<htm:tr rendered="#{container != 0 && process_item.container==container}" styleClass="standardTable_Row1">
				
					<htm:td>
						<h:outputText value="#{process_item.name}" />
					</htm:td>
					<htm:td>
						<h:outputText value="#{process_item.value}" />
					</htm:td>
			
					<htm:td styleClass="standardTable_ColumnCentered">
						<%-- edit container --%>
						<h:commandLink action="ProzessverwaltungBearbeiten" title="#{msgs.bearbeiten}" rendered="#{propInd + 1 == propCount}">
							<h:graphicImage value="/newpages/images/buttons/edit.gif" />
							<x:updateActionListener property="#{ProzessverwaltungForm.container}" value="#{container}" />
							<x:updateActionListener property="#{ProzessverwaltungForm.modusBearbeiten}" value="eigenschaft" />
							<%-- 	<a4j:support event="onchange" reRender="editBatch" /> --%>
						</h:commandLink>
						<%-- duplicate container --%>
						<h:commandLink action="#{ProzessverwaltungForm.duplicateContainer}" title="#{msgs.duplicate}" rendered="#{propInd + 1 == propCount}">
							<h:graphicImage value="/newpages/images/buttons/copy.gif" />
							<x:updateActionListener property="#{ProzessverwaltungForm.container}" value="#{container}" />
						</h:commandLink>
					</htm:td>
		
				</htm:tr>
			</x:dataList>	
			
			<htm:tr rendered="#{rowIndex + 1 < rowCount}">
				<htm:td colspan="3" styleClass="standardTable_Row1">
					<h:outputText value="&nbsp;" escape="false" />
				</htm:td>
			</htm:tr>
		</x:dataList>



</htm:table>


<%-- Neu-Schaltknopf --%>
<h:commandLink action="#{ProzessverwaltungForm.createNewProperty}" value="#{msgs.eigenschaftHinzufuegen}" title="#{msgs.eigenschaftHinzufuegen}"
	rendered="#{ProzessverwaltungForm.modusBearbeiten!='eigenschaft'}">
	<x:updateActionListener property="#{ProzessverwaltungForm.modusBearbeiten}" value="eigenschaft" />
</h:commandLink>


<%-- ++++++++++++++++++++++++++++++++++++++++++++++++++++ --%>
<%-- +++++++++++++++     Eigenschaft bearbeiten      ++++++++++++++++ --%>
<%-- ++++++++++++++++++++++++++++++++++++++++++++++++++++ --%>

<htm:h4 style="margin-top:15" rendered="#{ProzessverwaltungForm.modusBearbeiten=='eigenschaft'}">
	<h:outputText value="#{msgs.eigenschaftBearbeiten}" />
</htm:h4>
<%-- Box für die Bearbeitung der Details --%>
<htm:table cellpadding="3" cellspacing="0" width="100%" styleClass="eingabeBoxen" rendered="#{ProzessverwaltungForm.modusBearbeiten=='eigenschaft'}">

	<htm:tr>
		<htm:td styleClass="eingabeBoxen_row1" colspan="2">
			<h:outputText value="#{msgs.eigenschaft}" />
		</htm:td>
	</htm:tr>

	<%-- Formular für die Bearbeitung der Eigenschaft --%>
	<htm:tr>
		<htm:td styleClass="eingabeBoxen_row2" colspan="2">
				<htm:table>
					<x:dataList var="myprocess_item" value="#{ProzessverwaltungForm.containerProperties}">
						<htm:tr>		
							<htm:td>
								<%-- 	<x:aliasBean alias="#{myprocess_item}" value="#{ProzessverwaltungForm.processProperty}">--%>
								<h:outputText id="eigenschafttitel" style="width: 500px;margin-right:15px" value="#{myprocess_item.name}: " rendered="#{!myprocess_item.isNew}"/>
								
							</htm:td>
							<htm:td>
								
								<h:panelGroup rendered="#{myprocess_item.isNew}" >
									<h:inputText id="title" value="#{myprocess_item.name}" required="true" />
								<x:message for="title" style="color: red" detailFormat="#{msgs.keinTitelAngegeben}" />
								</h:panelGroup>
								
								<%-- textarea --%>
								<h:panelGroup id="prpvw15_1" rendered="#{((myprocess_item.type.name == 'text') || (myprocess_item.type.name == 'null'))}">
									<h:inputText id="file" style="width: 500px;margin-right:15px" value="#{myprocess_item.value}" />
								</h:panelGroup>
			
								<%-- numbers only --%>
								<h:panelGroup id="prpvw15_1mnk" rendered="#{myprocess_item.type.name == 'integer' || myprocess_item.type.name == 'number'}">
			
									<h:inputText id="numberstuff122334mnktodo" style="width: 500px;margin-right:15px" value="#{myprocess_item.value}">
										<f:validateLongRange minimum="0" />
									</h:inputText>
								</h:panelGroup>
			
								<%--  SelectOneMenu --%>
								<h:panelGroup id="prpvw15_2" rendered="#{(myprocess_item.type.name == 'list')}">
									<h:selectOneMenu value="#{myprocess_item.value}" style="width: 500px;margin-right:15px" id="prpvw15_2_1">
										<si:selectItems id="prpvw15_2_2" value="#{myprocess_item.possibleValues}" var="myprocess_items" itemLabel="#{myprocess_items}"
											itemValue="#{myprocess_items}" />
									</h:selectOneMenu>
								</h:panelGroup>
			
								<%--  SelectManyMenu --%>
								<h:panelGroup id="prpvw15_3" rendered="#{(myprocess_item.type.name == 'listmultiselect')}">
									<h:selectManyListbox id="prpvw15_3_1" style="width: 500px;margin-right:15px" value="#{myprocess_item.valueList}" size="5">
										<si:selectItems id="prpvw15_3_2" value="#{myprocess_item.possibleValues}" var="myprocess_items" itemLabel="#{myprocess_items}"
											itemValue="#{myprocess_items}" />
									</h:selectManyListbox>
								</h:panelGroup>
			
								<%--  Boolean --%>
								<h:panelGroup id="prpvw15_4" rendered="#{(myprocess_item.type.name == 'boolean')}">
									<h:selectOneMenu value="#{myprocess_item.booleanValue}" style="width: 500px;margin-right:15px" id="prpvw15_4_1">
										<f:selectItem id="prpvw15_4_2" itemValue="true" itemLabel="#{msgs.yes}" />
										<f:selectItem id="prpvw15_4_3" itemValue="false" itemLabel="#{msgs.no}" />
									</h:selectOneMenu>
								</h:panelGroup>
			
								<%--  Date  --%>
								<h:panelGroup id="prpvw15_5" rendered="#{(myprocess_item.type.name == 'date')}">
									<rich:calendar id="prpvw15_5_1" style="width: 500px;margin-right:15px" datePattern="dd.MM.yyyy" value="#{myprocess_item.value}"
										enableManualInput="true">
									</rich:calendar>
								</h:panelGroup>
							</htm:td>
						</htm:tr>	
					</x:dataList>
				</htm:table>
		</htm:td>
	</htm:tr>

	<htm:tr>
		<htm:td styleClass="eingabeBoxen_row3" align="left">
			<h:commandButton value="#{msgs.abbrechen}" action="#{NavigationForm.Reload}" immediate="true">
				<x:updateActionListener property="#{ProzessverwaltungForm.modusBearbeiten}" value="" />
			</h:commandButton>
		</htm:td>
		<htm:td styleClass="eingabeBoxen_row3" align="right">
			 
				<h:commandButton value="#{msgs.loeschen}" action="#{ProzessverwaltungForm.deleteProperty}"
					onclick="return confirm('#{msgs.sollDieserEintragWirklichGeloeschtWerden}?')">
					<x:updateActionListener property="#{ProzessverwaltungForm.modusBearbeiten}" value="" />
				</h:commandButton>
			

			<h:commandButton value="#{msgs.uebernehmen}" action="#{ProzessverwaltungForm.saveCurrentProperty}">

				<x:updateActionListener property="#{ProzessverwaltungForm.modusBearbeiten}" value="" />
			</h:commandButton>
		</htm:td>
	</htm:tr>


</htm:table>