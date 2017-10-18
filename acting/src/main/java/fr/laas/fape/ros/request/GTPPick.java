package fr.laas.fape.ros.request;

import fr.laas.fape.ros.database.Attachments;
import fr.laas.fape.ros.exception.ActionFailure;
import fr.laas.fape.ros.message.MessageFactory;
import gtp_ros_msg.requestGoal;


public class GTPPick extends GTPRequest {

    public final String agent;
    public final String object;
    public final String arm;

    public GTPPick(String agent, String object) {
        this.agent = agent;
        this.object = object;
        if(Attachments.isArmFree("left") && !Attachments.isArmFree("right"))
            arm = "left";
        else if(!Attachments.isArmFree("left") && Attachments.isArmFree("right"))
            arm = "right";
        else if(Attachments.isArmFree("left") && Attachments.isArmFree("right"))
            arm = null;
        else
            throw new RuntimeException("No free arm to perform pick");
    }

    @Override
    public requestGoal asActionGoal() {
        requestGoal goal = GTPRequest.emptyGoal();
        goal.getReq().setRequestType("planning");
        goal.getReq().setActionName("pick");
        goal.getReq().getInvolvedAgents().add(MessageFactory.getAg("mainAgent", agent));
        goal.getReq().getInvolvedObjects().add(MessageFactory.getObj("mainObject", object));
        if(arm != null)
            goal.getReq().getData().add(MessageFactory.getData("hand", arm));
        goal.getReq().getPredecessorId().setActionId(-1);
        goal.getReq().getPredecessorId().setAlternativeId(-1);
        return goal;
    }

    public void execute() throws ActionFailure {
        super.execute();

    }
}
