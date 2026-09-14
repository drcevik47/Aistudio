def fix_file(filename):
    with open(filename, 'r') as f:
        lines = f.readlines()
        
    stack = []
    
    for i, line in enumerate(lines):
        if "{" in line:
            for _ in range(line.count("{")):
                stack.append(i)
        if "}" in line:
            for _ in range(line.count("}")):
                if stack:
                    stack.pop()
    
    print("Unclosed braces at lines:", stack)

fix_file("app/src/main/java/com/example/ui/MainViewModel.kt")
