package com.github.ehdez73.code2req.infrastructure.cli.support;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class CommandSuggestionAspect {

    private final SuggestionService suggestionService;

    public CommandSuggestionAspect(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @Around("execution(* com.github.ehdez73.code2req.infrastructure.cli.command.*.*(..)) && @annotation(shellMethod)")
    public Object appendSuggestions(ProceedingJoinPoint joinPoint, ShellMethod shellMethod) throws Throwable {
        Object result = joinPoint.proceed();
        if (result instanceof String output) {
            String suggestions = suggestionService.suggest();
            if (!suggestions.isEmpty()) {
                return output + "\n" + suggestions;
            }
        }
        return result;
    }
}
