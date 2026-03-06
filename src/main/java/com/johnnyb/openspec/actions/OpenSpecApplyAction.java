package com.johnnyb.openspec.actions;

public class OpenSpecApplyAction extends OpenSpecCliAction {

    @Override
    protected String[] getCliArgs() {
        return new String[]{"apply"};
    }

    @Override
    protected String getCommandLabel() {
        return "apply";
    }
}
