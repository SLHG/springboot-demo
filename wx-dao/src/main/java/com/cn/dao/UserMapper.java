package com.cn.dao;


import com.cn.beans.wx.User;

import java.util.List;

public interface UserMapper {
    User getUser(Integer id);

    List<User> getUserByName(String name);
}
