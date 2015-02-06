
public class Test {
	static class Base
	{
	   private void foo()
	   {
		   System.out.println("Base");
	   }
	   public void a()
	   {
		   foo();
	   }
	}

	static class Child extends Base
	{
		private final void testMethod()
		{
			
		}
		
	    private void foo()
	    {
	    	System.out.println("Child");
	    }
	    
	   public void b()
	   {
		   foo();
	   }
	}

	public static void main(String[] args)
	{
		Base b = new Base();
		b.a();
		
		Child c = new Child();
		c.a();
		c.b();
		
		int  x= (int) (Math.random() * 10);
		System.out.println(x);
		
	}
}
