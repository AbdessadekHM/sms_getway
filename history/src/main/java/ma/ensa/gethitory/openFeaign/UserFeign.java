package ma.ensa.gethitory.openFeaign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import ma.ensa.gethitory.dto.User;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@FeignClient(value = "user", url = "127.0.0.1:7001/auth/")
public interface UserFeign {

    @GetMapping("user")
    Optional<User> getUser(@RequestParam  String username);
}
