package com.johnnyb.openspec.actions;

public class OpenSpecArchiveAction extends OpenSpecCliAction {

    @Override
    protected String[] getCliArgs() {
        return new String[]{"archive"};
    }

    @Override
    protected String getCommandLabel() {
        return "archive";
    }
}
