package com.gucare.health;

import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class PolicyEngineTest {
    private final LocalDate today=LocalDate.of(2026,8,31);
    private Profile age(int years){Profile p=new Profile();p.birth=today.minusYears(years).toString();p.sex="女";return p;}
    private PolicyEngine.Policy rule(Profile p,String id){return PolicyEngine.forProfile(p,today).stream().filter(x->x.id.equals(id)).findFirst().orElseThrow();}
    @Test public void exactBirthdaysMatter(){Profile p=age(45);p.birth=today.minusYears(45).plusDays(1).toString();assertEquals(44,p.age(today));assertFalse(rule(p,"colon").eligible);}
    @Test public void adultStartsAt30(){assertFalse(rule(age(29),"adult").eligible);assertTrue(rule(age(30),"adult").eligible);assertEquals(60,rule(age(39),"adult").intervalMonths);}
    @Test public void adultIntervals(){assertEquals(36,rule(age(40),"adult").intervalMonths);assertEquals(36,rule(age(64),"adult").intervalMonths);assertEquals(12,rule(age(65),"adult").intervalMonths);}
    @Test public void indigenousAdultAnnualAt55(){Profile p=age(55);p.indigenous=true;assertEquals(12,rule(p,"adult").intervalMonths);p.birth=today.minusYears(54).toString();assertEquals(36,rule(p,"adult").intervalMonths);}
    @Test public void polioAdultAnnualAt35(){Profile p=age(35);p.polio=true;assertEquals(12,rule(p,"adult").intervalMonths);}
    @Test public void colorectalRange(){assertFalse(rule(age(44),"colon").eligible);assertTrue(rule(age(45),"colon").eligible);assertTrue(rule(age(74),"colon").eligible);assertFalse(rule(age(75),"colon").eligible);}
    @Test public void colorectalFamilyException(){Profile p=age(40);p.familyColon=true;assertTrue(rule(p,"colon").eligible);p.birth=today.minusYears(39).toString();assertFalse(rule(p,"colon").eligible);}
    @Test public void breastSexAndAge(){assertFalse(rule(age(39),"breast").eligible);assertTrue(rule(age(40),"breast").eligible);assertFalse(rule(age(75),"breast").eligible);Profile p=age(60);p.sex="男";assertFalse(rule(p,"breast").eligible);}
    @Test public void cervicalIntervals(){assertFalse(rule(age(24),"cervical").eligible);assertEquals(36,rule(age(25),"cervical").intervalMonths);assertEquals(12,rule(age(30),"cervical").intervalMonths);}
    @Test public void hpvDiscreteAges(){assertTrue(rule(age(35),"hpv").eligible);assertTrue(rule(age(45),"hpv").eligible);assertTrue(rule(age(65),"hpv").eligible);assertFalse(rule(age(46),"hpv").eligible);}
    @Test public void hpvDoesNotInventNextDueDate(){assertNull(PolicyEngine.nextReview(rule(age(35),"hpv"),today));}
    @Test public void oldHpvRecordDoesNotConsumeNextAgeBenefit(){Profile p=age(45);assertFalse(PolicyEngine.hpvUsedForCurrentAge(p,today,today.minusYears(10)));assertTrue(PolicyEngine.hpvUsedForCurrentAge(p,today,today));}
    @Test public void lungFamilyDependsOnAgeAndSex(){Profile p=age(40);p.familyLung=true;assertTrue(rule(p,"lung").eligible);p.sex="男";assertFalse(rule(p,"lung").eligible);p.birth=today.minusYears(45).toString();assertTrue(rule(p,"lung").eligible);}
    @Test public void smokerNeedsAgeAndPackYears(){Profile p=age(50);p.smokes=true;p.packYears=19;assertFalse(rule(p,"lung").eligible);p.packYears=20;assertTrue(rule(p,"lung").eligible);p.birth=today.minusYears(49).toString();assertFalse(rule(p,"lung").eligible);}
    @Test public void formerSmokingMustBeKnown(){Profile p=age(60);p.packYears=25;assertFalse(rule(p,"lung").eligible);p.quitYears=15;assertTrue(rule(p,"lung").eligible);p.quitYears=16;assertFalse(rule(p,"lung").eligible);}
    @Test public void oralExceptions(){Profile p=age(20);p.betel=true;assertFalse(rule(p,"oral").eligible);p.indigenous=true;assertTrue(rule(p,"oral").eligible);p=age(30);p.smokes=true;assertTrue(rule(p,"oral").eligible);}
    @Test public void followUpsAreNotPublicSubsidies(){Profile p=age(72);assertFalse(rule(p,"dental").publicService);assertFalse(rule(p,"eye").publicService);assertNull(PolicyEngine.nextReview(rule(p,"eye"),today));}
    @Test public void leapDayReview(){Profile p=age(65);assertEquals(LocalDate.of(2025,2,28),PolicyEngine.nextReview(rule(p,"adult"),LocalDate.of(2024,2,29)));}
    @Test public void missingRecordDoesNotBecomeOverdue(){assertNull(PolicyEngine.nextReview(rule(age(60),"colon"),null));}
}
