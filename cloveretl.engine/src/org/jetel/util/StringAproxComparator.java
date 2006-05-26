package org.jetel.util;

import java.text.Collator;
import java.util.Locale;

/**
 * Class for aproximative string comparison
 * 
 * @author avackova
 *
 */

public class StringAproxComparator {

	//strenth fields
	private final static int  IDENTICAL=4;
	private final static int TERTIARY=3; //upper case = lower case
	private final static int SECONDARY=2;//diacrtic letters = letters witout diacrityk (locale dependent)
			// now done only for CZ
	private final static int PRIMARY=1;// mistakes acceptable (Collator.PRIMARY,new Locale("en","UK"))
	
	private int strenth;
	private int substCost=1;
	private int delCost=1;
	private int changCost=1;
	private String[] rules={};

	//arrays for method distance
	char[] schars;
	char[] tchars;
	int[] slast = null;
	int[] tlast = null;
	int[] tblast = null;
	int[] now = null;

	Collator col;

	/**
	 * @param strenth = comparison level
	 * @param changCost = cost of char change operation
	 * @param delCost = cost of char deletion operation
	 * @param substCost = cost of char substitution operation
	 */
	public StringAproxComparator(int strenth, int changCost, int delCost, int substCost) {
		this.setStrenth(strenth);
		this.changCost=changCost;
		this.delCost=delCost;
		this.substCost=substCost;
	}

	/**
	 * @param locale = parameter for getting rules from StringAproxComparatorLocaleRules
	 * @param strenth = comparison level
	 * @param changCost = cost of char change operation
	 * @param delCost = cost of char deletion operation
	 * @param substCost = cost of char substitution operation
	 */
	public StringAproxComparator(String locale,int strenth, int changCost, int delCost, int substCost) {
		this(locale);
		this.setStrenth(strenth);
		this.changCost=changCost;
		this.delCost=delCost;
		this.substCost=substCost;
	}

	public StringAproxComparator(int changCost, int delCost, int substCost) {
		strenth = IDENTICAL;
		this.changCost=changCost;
		this.delCost=delCost;
		this.substCost=substCost ;
	}

	/**
	 * @param strenth = comparison level
	 */
	public StringAproxComparator(int strenth) {
		this.setStrenth(strenth);
	}

	/**
	 * @param locale = parameter for getting rules from StringAproxComparatorLocaleRules
	 * @param strenth = comparison level
	 */
	public StringAproxComparator(String locale,int strenth) {
		this(locale);
		this.setStrenth(strenth);
	}

	public StringAproxComparator() {
		strenth = IDENTICAL;
	}
	
	public StringAproxComparator(String locale) {
		try {
			rules=StringAproxComparatorLocaleRules.getRules(locale);
		}catch(NoSuchFieldException e){
			System.out.println(e.getMessage());
		}
	}
	
	/**
	 * Method for geting rules for one character - locale dependent  
	 *  
	 * @param c char for witch the rules are to be
	 * @param rules set of rule for given locale
	 * @return
	 */
	private int getRules(char c,String[] rules){
		int i;
		for (i=0;i<rules.length;i++){
			String rule=rules[i];
			if (!(rule.indexOf(c)==-1)) break;
		}
		return i;
	}
	
	public boolean charEquals(char c1,char c2,int strenth){
		int c;
		switch (strenth) {
			case IDENTICAL:
				return c1==c2;
			case TERTIARY:
				c=Math.min((int)c1+32,(int)c2+32);
				return (c1==c2 || Math.max((int)c1,(int)c2)==c);
			case SECONDARY:
				c=getRules(c1,rules);
				if (c<rules.length)
				   return (rules[c].indexOf(c2)!=-1);
				else {
				   return (charEquals(c1,c2,StringAproxComparator.TERTIARY));
				}
			case PRIMARY:
				boolean p1=col.compare(String.valueOf(c1),String.valueOf(c2))==0;
				return (p1 || charEquals(c1,c2,StringAproxComparator.SECONDARY));
		}
		return false;
	}
	
	private int subst_cost(char c1,char c2){
		return (charEquals(c1,c2,strenth) ? 0 : substCost);
	}
	
	private int min(int i1,int i2,int i3){
		int m=i1;
		if (i2<m) m=i2;
		if (i3<m) m=i3;
		return m;
	}
	
	public int distance(String s,String t){
		if (s.length()==0 || t.length()==0)
			return Math.min(s.length(),t.length());

		int slenth=s.length()+1;
		int tlent=t.length()+1;
		
		
		 if (slast==null){
			slast = new int[slenth];
			tlast = new int[tlent];
			tblast = new int[tlent];
			now = new int[tlent];
		 }
		if (slast.length<slenth)
			slast = new int[tlent];
		if (tlast.length<tlent){
			tlast = new int[tlent];
			tblast = new int[tlent];
			now = new int[tlent];
		}
		 
		for (int i=0;i<slenth;i++)
			slast[i]=i;
		for (int i=0;i<tlent;i++)
			tlast[i]=i;

		schars=s.toCharArray();
		tchars=t.toCharArray();
		
		for (int i=1;i<slenth;i++) {
			now[0]=slast[i];
			tlast[0]=slast[i-1];
			for (int j=1;j<tlent;j++){
				int m=min(now[j-1]+delCost,tlast[j]+delCost,tlast[j-1]+subst_cost(schars[i-1],tchars[j-1]));
				if (i>1 && j>1)
					if (schars[i-2]==tchars[j-1]&&schars[i-1]==tchars[j-2])
						m=Math.min(m,tblast[j-2]+changCost);
				now[j]=m;
			}
			for (int j=0;j<tlent;j++){
				tblast[j]=tlast[j];
				tlast[j]=now[j];
			}
		}
		
		return tlast[tlent-1];
		
	}

	/**
	 * This method sets cost of change operation
	 * @param changCost cost of char change
	 */
	public void setChangeCost(int changCost) {
		this.changCost = changCost;
	}

	/**
	 * @param delCost
	 */
	public void setDelCost(int delCost) {
		this.delCost = delCost;
	}

	public void setSubstitutionCost(int substCost) {
		this.substCost = substCost;
	}

	public int getStrenth() {
		return strenth;
	}

	public void setStrenth(int strenth) {
		this.strenth = strenth;
		if (strenth==StringAproxComparator.PRIMARY&&col==null){
			col=Collator.getInstance(new Locale("US"));
			col.setStrength(Collator.PRIMARY);
		}
	}

}
