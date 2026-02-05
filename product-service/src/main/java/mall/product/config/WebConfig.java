package mall.product.config;

import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        String path = new File(uploadDir).getAbsolutePath();

        if (!path.endsWith(File.separator)) {
            path += File.separator;
        }

        registry.addResourceHandler("/products/images/**")
                .addResourceLocations("file:" + path + "/");
    }
}