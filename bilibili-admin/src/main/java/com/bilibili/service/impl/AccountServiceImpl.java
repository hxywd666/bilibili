package com.bilibili.service.impl;

import cn.hutool.core.lang.UUID;
import com.bilibili.constant.AccountConstant;
import com.bilibili.constant.MessageConstant;
import com.bilibili.exception.LoginErrorException;
import com.bilibili.exception.ParamErrorException;
import com.bilibili.pojo.dto.LoginDTO;
import com.bilibili.pojo.vo.CheckCodeVO;
import com.bilibili.properties.AdminProperties;
import com.bilibili.result.Result;
import com.bilibili.service.AccountService;
import com.wf.captcha.ArithmeticCaptcha;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AccountServiceImpl implements AccountService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private AdminProperties adminProperties;

    @Override
    public Result<CheckCodeVO> getCheckCode() {
        //获取验证码答案和图片 并存入Redis
        ArithmeticCaptcha captcha = new ArithmeticCaptcha(AccountConstant.CAPTCHA_WIDTH, AccountConstant.CAPTCHA_HEIGHT);
        String captchaCode = captcha.text();
        String captchaImage = captcha.toBase64();
        String redisKey = AccountConstant.ADMIN_KEY_PREFIX
                + AccountConstant.CAPTCHA_REDIS_KEY
                + UUID.randomUUID(true);
        redisTemplate.opsForValue().set(redisKey, captchaCode, AccountConstant.CAPTCHA_EXPIRE, TimeUnit.MILLISECONDS);
        CheckCodeVO checkCodeVO = CheckCodeVO.builder()
                .checkCode(captchaImage)
                .checkCodeKey(redisKey)
                .build();
        return Result.success(checkCodeVO);
    }

    @Override
    public Result login(HttpServletRequest request, HttpServletResponse response, LoginDTO loginDTO) {
        // 参数校验
        if (!StringUtils.hasText(loginDTO.getCheckCode()) || !StringUtils.hasText(loginDTO.getCheckCodeKey())) {
            throw new ParamErrorException(MessageConstant.PARAM_ERROR);
        }
        if (!StringUtils.hasText(loginDTO.getAccount())) {
            throw new LoginErrorException(MessageConstant.ACCOUNT_OR_PASSWORD_ERROR);
        }

        //检查验证码并删除
        String captcha = (String) redisTemplate.opsForValue().get(loginDTO.getCheckCodeKey());
        if (captcha == null || !captcha.equalsIgnoreCase(loginDTO.getCheckCode())) {
            redisTemplate.delete(loginDTO.getCheckCodeKey());
            throw new LoginErrorException(MessageConstant.CAPTCHA_ERROR);
        }
        redisTemplate.delete(loginDTO.getCheckCodeKey());

        //检查邮箱、密码(前端已经加密过了)、账户状态
        if ( !loginDTO.getAccount().equals(adminProperties.getAccount()) || !loginDTO.getPassword().equals(adminProperties.getPassword())) {
            throw new LoginErrorException(MessageConstant.ACCOUNT_OR_PASSWORD_ERROR);
        }

        //生成token 存入Redis、Cookie
        String token = UUID.randomUUID(true).toString();
        redisTemplate.opsForValue().set(
                AccountConstant.ADMIN_KEY_PREFIX + AccountConstant.LOGIN_REDIS_KEY + token,
                loginDTO.getAccount(),
                AccountConstant.TOKEN_EXPIRE,
                TimeUnit.MILLISECONDS
        );

        //删除上一次的token(非必要)
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(AccountConstant.ADMIN_COOKIE_KEY) && StringUtils.hasText(cookie.getValue())) {
                    redisTemplate.delete(AccountConstant.ADMIN_KEY_PREFIX + AccountConstant.LOGIN_REDIS_KEY + cookie.getValue());
                    break;
                }
            }
        }

        //将新token存入Cookie返回
        Cookie cookie = new Cookie(AccountConstant.ADMIN_COOKIE_KEY, token);
        cookie.setMaxAge(AccountConstant.TOKEN_EXPIRE / 1000);
        cookie.setPath("/");
        response.addCookie(cookie);

        return Result.success(loginDTO.getAccount());
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(AccountConstant.ADMIN_COOKIE_KEY) && StringUtils.hasText(cookie.getValue())) {
                redisTemplate.delete(AccountConstant.ADMIN_KEY_PREFIX + AccountConstant.LOGIN_REDIS_KEY + cookie.getValue());
                cookie.setMaxAge(0);
                cookie.setPath("/");
                response.addCookie(cookie);
                break;
            }
        }
    }
}