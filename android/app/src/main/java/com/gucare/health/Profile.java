package com.gucare.health;

import java.time.LocalDate;
import java.time.Period;

public class Profile {
    public long id;
    public String name = "", birth = "1954-03-15", sex = "女", city = "";
    public boolean indigenous, polio, familyColon, familyLung, smokes, betel;
    public int packYears, quitYears = -1;
    public int age(LocalDate date) { return Period.between(LocalDate.parse(birth), date).getYears(); }
}
