package dev.sivalabs.helpdesk.residents;

record CreateResidentCmd(
        String name, String email, String password, String flatNumber, String mobile, String permanentAddress) {}
