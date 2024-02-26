package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.service.UserService;
import com.sky.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatProperties weChatProperties;
    public  static  final  String WX_LOGIN="https://api.weixin.qq.com/sns/jscode2session";

    @Override
    public User wxLogin(UserLoginDTO userLoginDTO) {
        String openid=getOpenId(userLoginDTO.getCode());
        //判断openid是否为空，若为空表示登录失败
        if(openid==null){
            throw  new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        User user =userMapper.getByOpenId(openid);

        //如果是新用户，自动完成注册
        if(user==null){
            user= User.builder().openid(openid).createTime(LocalDateTime.now()).build();
            userMapper.insert(user);
        }

        //返回用户对象
        return user;
    }

    private String getOpenId(String code){
        //调用微信接口服务，获得当前用户的openid
        Map<String,String> map=new HashMap<>();
        map.put("appid", weChatProperties.getAppid());
        map.put("secret", weChatProperties.getSecret());
        map.put("js_code",code);
        map.put("grant_type", "authorization_code");

        String json=HttpClientUtil.doGet(WX_LOGIN,map);

        JSONObject jsonObject= JSON.parseObject(json);

        String openid=jsonObject.getString("openid");
        return openid;
    }
}
