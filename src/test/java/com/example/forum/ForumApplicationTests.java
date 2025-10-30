package java.com.example.forum;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.injector.methods.SelectList;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.forum.entity.Post;
import com.example.forum.repo.PostRepo;
import com.example.forum.service.PostService;
import com.example.forum.service.PostServiceImpl;

import jakarta.annotation.PostConstruct;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.beans.Encoder;
import java.sql.SQLException;
import java.util.List;

@SpringBootTest
class ForumApplicationTests {




    @Test
    void bcryptMatches() {
        System.out.println(new BCryptPasswordEncoder().encode("123312q"));
    }
}

