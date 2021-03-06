package org.goobi.goobiScript;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Process;
import org.goobi.beans.Processproperty;
import org.goobi.production.enums.GoobiScriptResultType;

import de.sub.goobi.helper.Helper;
import de.sub.goobi.persistence.managers.ProcessManager;
import de.sub.goobi.persistence.managers.PropertyManager;

public class GoobiScriptPropertySet extends AbstractIGoobiScript implements IGoobiScript {

    @Override
    public boolean prepare(List<Integer> processes, String command, HashMap<String, String> parameters) {
        super.prepare(processes, command, parameters);

        if (StringUtils.isBlank(parameters.get("name"))) {
            Helper.setFehlerMeldung("goobiScriptfield", "Missing parameter: ", "name");
            return false;
        }

        if (StringUtils.isBlank(parameters.get("value"))) {
            Helper.setFehlerMeldung("goobiScriptfield", "Missing parameter: ", "value");
            return false;
        }
        // add all valid commands to list
        for (Integer i : processes) {
            GoobiScriptResult gsr = new GoobiScriptResult(i, command, username);
            resultList.add(gsr);
        }

        return true;
    }

    @Override
    public void execute() {
        TEMPLATEThread et = new TEMPLATEThread();
        et.start();
    }

    class TEMPLATEThread extends Thread {
        @Override
        public void run() {

            String propertyName = parameters.get("name");
            String value = parameters.get("value");

            // execute all jobs that are still in waiting state
            ArrayList<GoobiScriptResult> templist = new ArrayList<>(resultList);
            for (GoobiScriptResult gsr : templist) {
                if (gsm.getAreScriptsWaiting(command) && gsr.getResultType() == GoobiScriptResultType.WAITING && gsr.getCommand().equals(command)) {
                    Process p = ProcessManager.getProcessById(gsr.getProcessId());
                    gsr.setProcessTitle(p.getTitel());
                    gsr.setResultType(GoobiScriptResultType.RUNNING);
                    gsr.updateTimestamp();
                    boolean matched = false;
                    for (Processproperty pp : p.getEigenschaften()) {
                        if (pp.getTitel().equals(propertyName)) {
                            pp.setWert(value);
                            PropertyManager.saveProcessProperty(pp);
                            gsr.setResultMessage("Property updated.");
                            gsr.setResultType(GoobiScriptResultType.OK);
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) {
                        Processproperty pp = new Processproperty();
                        pp.setTitel(propertyName);
                        pp.setWert(value);
                        pp.setProzess(p);
                        PropertyManager.saveProcessProperty(pp);
                        gsr.setResultMessage("Property created.");
                        gsr.setResultType(GoobiScriptResultType.OK);
                    }

                    gsr.updateTimestamp();
                }
            }
        }
    }

}
