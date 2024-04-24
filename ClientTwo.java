package com.example.chatApp;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.io.*;
import java.text.SimpleDateFormat;
import org.apache.zookeeper.AddWatchMode;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

public class ClientTwo {

    public static void main(String[] args) throws Exception {
        //Gets the user's userName
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter your username: ");
        String userName = scanner.nextLine();

        //Start the ZooKeeper Connection
        CountDownLatch latch = new CountDownLatch(1);
        Watcher watcher = event -> {
            System.out.println("===Connected to the ZooKeeper Server===");
        };
        ZooKeeper zooKeeper = new ZooKeeper("127.0.0.1", 2181, watcher);

        String path = "/" + userName;
        byte[] userData = userName.getBytes();
        Stat stat = zooKeeper.exists(path, true);

        if (stat == null) {
            zooKeeper.create(path, userData, Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        } else {
            zooKeeper.setData(path, userData, -1);
        }

        //setting up the watcher to check for newly connected users
        zooKeeper.addWatch("/", event -> {
            if (event.getType() != Watcher.Event.EventType.NodeCreated) {
                try {
                    String connectedMessage = "====" + userName + " connected===";
                    chatLogDB.chatLog.add(connectedMessage);

                    Stat statWatch1 = new Stat();
                    byte[] currentData = zooKeeper.getData("/discordApp", false, statWatch1);

                    ByteArrayInputStream bis = new ByteArrayInputStream(currentData);
                    ObjectInputStream in = new ObjectInputStream(bis);
                    Object obj = in.readObject();
                    List<String> msgList = (List<String>) obj;

                    msgList.add(connectedMessage);

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ObjectOutputStream out = new ObjectOutputStream(bos);
                    out.writeObject(msgList);
                    byte[] listOfMessages =  bos.toByteArray();
                    zooKeeper.setData("/discordApp", listOfMessages, statWatch1.getVersion());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, AddWatchMode.PERSISTENT);

        //Getting the previous messages sent on the zooKeeper
        byte[] data = zooKeeper.getData("/discordApp", watcher, null);

        if(data!=null){
            ByteArrayInputStream bis2 = new ByteArrayInputStream(data);
            ObjectInput in2 = new ObjectInputStream(bis2);
            Object obj2 = in2.readObject();
            List<String> messageDB = (List<String>) obj2;
            
            for (String item : messageDB) {
                System.out.println(item);
            }
        }

        //Creating the discordApp branch(acts like Data base for the messages)
        Stat chatStat = zooKeeper.exists("/discordApp", false);
        if (chatStat == null) {
            zooKeeper.create("/discordApp", null, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }

        zooKeeper.addWatch("/discordApp", event -> {
            if (event.getType() == Watcher.Event.EventType.NodeDataChanged) {
                try {
                    byte[] dataArr = zooKeeper.getData("/discordApp", watcher, null);

                    ByteArrayInputStream bis3 = new ByteArrayInputStream(dataArr);
                    ObjectInputStream in3 = new ObjectInputStream(bis3);
                    Object obj3 = in3.readObject();
                    List<String> msgDB = (List<String>) obj3;

                    for (String item : msgDB) {
                        System.out.println(item);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, AddWatchMode.PERSISTENT);


        //Sending the messages
        while (true) {
            System.out.println("Enter your message:");
            System.out.println("-1 to exit)");
            String message = scanner.nextLine();

            //Chekcing if the user wants to disconnects
            if (message.equals("-1")) {
                String disconnectedMessage = "=====" + userName + "  has disconnected====";
                chatLogDB.chatLog.add(disconnectedMessage);

                Stat chatroomStat = new Stat();
                byte[] currentData = zooKeeper.getData("/discordApp", false, chatroomStat);

                ByteArrayInputStream bis4 = new ByteArrayInputStream(currentData);
                ObjectInputStream in4 = new ObjectInputStream(bis4);
                Object obj4 = in4.readObject();
                List<String> msgList = (List<String>) obj4;

                if (!msgList.isEmpty()) {
                    String lastMessage = msgList.get(msgList.size() - 1);
                    String[] parts = lastMessage.split(":");

                    if (parts.length >= 2 && parts[0].trim().equals(userName)) {
                        msgList.remove(msgList.size() - 1);
                    }
                } else {
                    System.out.println("Chatroom is empty");
                }

                msgList.add(disconnectedMessage);
                
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(bos);
                out.writeObject(msgList);
                byte[] listOfMessages = bos.toByteArray();
                zooKeeper.setData("/discordApp", listOfMessages, chatroomStat.getVersion());

                System.exit(1);;
            }

            //sending messages
            Stat statMsg = new Stat();
            byte[] currentData = zooKeeper.getData("/discordApp", false, statMsg);
            List<String> msgList;
            
            if(currentData == null){
                msgList = new ArrayList<>();
            } else {
                ByteArrayInputStream bis5 = new ByteArrayInputStream(currentData);
                ObjectInputStream in5 = new ObjectInputStream(bis5);
                Object obj5 = in5.readObject();
                msgList = (List<String>)obj5;
            }

            Date currentDate = new Date();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String time = dateFormat.format(currentDate);

            String formattedMessage = "The user: " + userName + " sent a new message -> (" + time + ") -> " + message;
            
            msgList.add(formattedMessage);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(msgList);
            byte[] listOfMessages = bos.toByteArray();
            zooKeeper.setData("/discordApp", listOfMessages, statMsg.getVersion());
            if (latch.getCount() == 0) {
                break;
            }
            latch.await();
        }
    }
}
