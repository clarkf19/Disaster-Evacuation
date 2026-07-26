package com.mumbai.evacuation.dto;

import java.util.List;

public class DisasterProtectionGuideDTO {
    private String disasterType;
    private String title;
    private String icon;
    private String summary;
    private List<String> immediateActions;
    private List<FirstAidStep> firstAidSteps;
    private List<String> dos;
    private List<String> donts;
    private List<String> essentialKit;

    public DisasterProtectionGuideDTO() {}

    public DisasterProtectionGuideDTO(String disasterType, String title, String icon, String summary,
                                      List<String> immediateActions, List<FirstAidStep> firstAidSteps,
                                      List<String> dos, List<String> donts, List<String> essentialKit) {
        this.disasterType = disasterType;
        this.title = title;
        this.icon = icon;
        this.summary = summary;
        this.immediateActions = immediateActions;
        this.firstAidSteps = firstAidSteps;
        this.dos = dos;
        this.donts = donts;
        this.essentialKit = essentialKit;
    }

    public String getDisasterType() { return disasterType; }
    public void setDisasterType(String disasterType) { this.disasterType = disasterType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<String> getImmediateActions() { return immediateActions; }
    public void setImmediateActions(List<String> immediateActions) { this.immediateActions = immediateActions; }

    public List<FirstAidStep> getFirstAidSteps() { return firstAidSteps; }
    public void setFirstAidSteps(List<FirstAidStep> firstAidSteps) { this.firstAidSteps = firstAidSteps; }

    public List<String> getDos() { return dos; }
    public void setDos(List<String> dos) { this.dos = dos; }

    public List<String> getDonts() { return donts; }
    public void setDonts(List<String> donts) { this.donts = donts; }

    public List<String> getEssentialKit() { return essentialKit; }
    public void setEssentialKit(List<String> essentialKit) { this.essentialKit = essentialKit; }

    public static class FirstAidStep {
        private String stepTitle;
        private String instruction;
        private String warning;

        public FirstAidStep() {}

        public FirstAidStep(String stepTitle, String instruction, String warning) {
            this.stepTitle = stepTitle;
            this.instruction = instruction;
            this.warning = warning;
        }

        public String getStepTitle() { return stepTitle; }
        public void setStepTitle(String stepTitle) { this.stepTitle = stepTitle; }

        public String getInstruction() { return instruction; }
        public void setInstruction(String instruction) { this.instruction = instruction; }

        public String getWarning() { return warning; }
        public void setWarning(String warning) { this.warning = warning; }
    }
}
