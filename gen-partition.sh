./mill chiselModule.runMain top.TopMain -td build --output-file FireSim_A.v --infer-rw NutShellFPGATop --repl-seq-mem -c:NutShellFPGATop:-o:build/FireSim_A.v.conf BOARD=FireSim_A   CORE=inorder
#./mill chiselModule.runMain top.TopMain -td build --output-file FireSim_B.v --infer-rw NutShellFPGATop --repl-seq-mem -c:NutShellFPGATop:-o:build/TopMain.v.conf BOARD=FireSim_B   CORE=inorder
