int a =3;

function void print_result(int from,int to, int format) {
	if (isnull(format)) {
        if (try_convert(from,to)) 
        	print_err('converted:'+from+'-->'+to);
        else {
        	print_err('cant convert:'+from+'-->'+to);
        }
   	} else {
        if (try_convert(from,to, format)) 
        	print_err('converted:'+from+'-->'+to);
        else 
        	print_err('cant convert:'+from+'-->'+to+' with pattern '+format);
    }
}