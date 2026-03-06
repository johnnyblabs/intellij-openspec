package com.johnnyb.openspec.actions;

public class OpenSpecListAction extends OpenSpecCliAction {

    @Override
    protected String[] getCliArgs() {
        return new String[]{"list"};
    }

    @Override
    protected String getCommandLabel() {
        return "list";
    }
}
