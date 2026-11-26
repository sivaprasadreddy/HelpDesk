package dev.sivalabs.helpdesk.config;

import dev.sivalabs.helpdesk.shared.ResourceNotFoundException;
import dev.sivalabs.helpdesk.users.AuthUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ModelAndView handle(ResourceNotFoundException e) {
        LOG.info("Not found: {}", e.getMessage());
        return handleException(e, "error/404", HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ModelAndView handle(NoResourceFoundException e) {
        LOG.debug("No resource: {}", e.getMessage());
        return handleException(e, "error/404", HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ModelAndView handle(AccessDeniedException e) {
        LOG.warn("Access denied for user {}: {}", AuthUtils.getCurrentUserId(), e.getMessage());
        return handleException(e, "error/403", HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    ModelAndView handle(Exception e) {
        LOG.error("Unexpected error while handling a request", e);
        return handleException(e, "error/500", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ModelAndView handleException(Exception e, String viewName, HttpStatus httpStatus) {
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.addObject("message", e.getMessage());
        modelAndView.addObject("exception", e);
        modelAndView.setViewName(viewName);
        modelAndView.setStatus(httpStatus);
        return modelAndView;
    }
}
