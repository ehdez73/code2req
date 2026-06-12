package com.github.ehdez73.code2req.shell;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class HelloWorldCommand {

    @ShellMethod(key = "hello", value = "Prints a hello world message")
    public String hello() {
        return "Hello World from AI Reverse Engineering CLI!";
    }
}
