package fr.laas.fape.ros.database;


import fr.laas.fape.ros.ROSNode;
import geometry_msgs.Pose;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Subscriber;
import toaster_msgs.*;

import java.lang.Object;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Database {

    private static Database instance;

    public static final String root = "fape/";

    public static void initialize() {
        if(instance == null) {
            instance = new Database();
        }
    }

    public static Database getInstance() {
        if(instance == null) {
            initialize();
        }
        try {
            // make sure we have received the first messages before returning the database
            if(!instance.robotsCD.await(5, TimeUnit.SECONDS))
                throw new RuntimeException("No robot in database after waiting for 5 seconds. Make sure toaster is running.");
            if(!instance.objectsCD.await(5, TimeUnit.SECONDS))
                throw new RuntimeException("No objects in database after waiting for 5 seconds. Make sure toaster is running.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return instance;
    }

    private Map<String,DBEntry> db = new HashMap<>();

    private final CountDownLatch robotsCD = new CountDownLatch(1);
    private final CountDownLatch objectsCD = new CountDownLatch(1);

    private Database() {
        ConnectedNode node = ROSNode.getNode();
        Subscriber<RobotListStamped> robotsSub = node.newSubscriber("pdg/robotList", RobotListStamped._TYPE);
        robotsSub.addMessageListener(msg -> {
            if(!msg.getRobotList().isEmpty())
                robotsCD.countDown();
            for(toaster_msgs.Robot o : msg.getRobotList()) {
                String id = o.getMeAgent().getMeEntity().getName();
                Pose pose = o.getMeAgent().getMeEntity().getPose();
                if(!db.containsKey(id)) {
                    db.put(id, new DBEntry());
                }
                db.get(id).pose = pose;
            }
        });

        Subscriber<ObjectListStamped> objectsSub = node.newSubscriber("pdg/objectList", ObjectListStamped._TYPE);
        objectsSub.addMessageListener(msg -> {
            if(!msg.getObjectList().isEmpty())
                objectsCD.countDown();
            for(toaster_msgs.Object o : msg.getObjectList()) {
                String id = o.getMeEntity().getName();
                Pose pose = o.getMeEntity().getPose();
                if(!db.containsKey(id)) {
                    db.put(id, new DBEntry());
                }
                db.get(id).pose = pose;
            }
        });

//        if(ROSNode.getNode().getParameterTree().has(root+"seen"))
//            ROSNode.getNode().getParameterTree().delete(root+"seen");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private class DBEntry {
        geometry_msgs.Pose pose;
    }

    public static Pose getPoseOf(String object) {
        Database db = getInstance();
        if(!db.db.containsKey(object))
            throw new IllegalArgumentException("Unknown object: "+object);
        return db.db.get(object).pose;
    }

    public static Set<String> getObjects() {
        return getInstance().db.keySet();
    }

    public static Set<String> getTables() {
        return getInstance().db.keySet().stream().filter(o -> o.contains("TABLE")).collect(Collectors.toSet());
    }

    public static void setInt(String param, int i) {
        ROSNode.getNode().getParameterTree().set(root+param, i);
    }


    public static void setBool(String param, boolean i) {
        ROSNode.getNode().getParameterTree().set(root+param, i);
    }

    public static void setString(String param, String i) {
        ROSNode.getNode().getParameterTree().set(root+param, i);
    }

    public static boolean getBool(String param) {
        return ROSNode.getNode().getParameterTree().getBoolean(root+param);
    }

    public static int getInt(String param) {
        return ROSNode.getNode().getParameterTree().getInteger(root+param);
    }

    public static String getString(String param) {
        return ROSNode.getNode().getParameterTree().getString(root+param);
    }

    public static boolean getBool(String param, boolean def) {
        return ROSNode.getNode().getParameterTree().getBoolean(root+param, def);
    }

    public static int getInt(String param, int def) {
        return ROSNode.getNode().getParameterTree().getInteger(root+param, def);
    }

    public static String getString(String param, String def) {
        return ROSNode.getNode().getParameterTree().getString(root+param, def);
    }
}
