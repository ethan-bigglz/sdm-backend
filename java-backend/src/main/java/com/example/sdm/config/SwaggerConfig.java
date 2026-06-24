package com.example.sdm.config;
 
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
 
import java.util.Map;
import java.util.TreeMap;
 
@Configuration
public class SwaggerConfig {
 
    @Bean
    public OpenApiCustomizer sortOperationsBySummary() {
        return openApi -> {
            Paths paths = openApi.getPaths();
            if (paths == null) return;
 
            Map<String, PathItem> sortedPaths = new TreeMap<>((path1, path2) -> {
                PathItem p1 = paths.get(path1);
                PathItem p2 = paths.get(path2);
 
                String summary1 = getFirstOperationSummary(p1);
                String summary2 = getFirstOperationSummary(p2);
 
                return summary1.compareTo(summary2);
            });
 
            sortedPaths.putAll(paths);
 
            Paths newPaths = new Paths();
            newPaths.putAll(sortedPaths);
            openApi.setPaths(newPaths);
        };
    }
 
    private String getFirstOperationSummary(PathItem pathItem) {
        if (pathItem == null) return "";
        if (pathItem.getGet() != null && pathItem.getGet().getSummary() != null) return pathItem.getGet().getSummary();
        if (pathItem.getPost() != null && pathItem.getPost().getSummary() != null) return pathItem.getPost().getSummary();
        if (pathItem.getPut() != null && pathItem.getPut().getSummary() != null) return pathItem.getPut().getSummary();
        if (pathItem.getDelete() != null && pathItem.getDelete().getSummary() != null) return pathItem.getDelete().getSummary();
        if (pathItem.getPatch() != null && pathItem.getPatch().getSummary() != null) return pathItem.getPatch().getSummary();
        return "";
    }
}
