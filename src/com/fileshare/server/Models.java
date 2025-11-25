package com.fileshare.server;

import java.io.Serializable;
import java.util.*;

class User implements Serializable {
    String username;
    String passwordHash; // salted
    String salt;
    Set<String> groupIds = new HashSet<>(); // membership
    Map<String, Boolean> leaderByGroup = new HashMap<>(); // groupId -> isLeader
}

class Group implements Serializable {
    String id; // simple slug
    String name;
    Set<String> members = new HashSet<>();
    String leader; // username
}

