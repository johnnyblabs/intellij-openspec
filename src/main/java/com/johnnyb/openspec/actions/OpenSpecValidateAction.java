package com.johnnyb.openspec.actions;

public class OpenSpecValidateAction extends OpenSpecCliAction {

    @Override
    protected String[] getCliArgs() {
        return new String[]{"validate"};
    }

    @Override
    protected String getCommandLabel() {
        return "validate";
    }
}
