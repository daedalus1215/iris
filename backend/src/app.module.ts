import { Module } from '@nestjs/common';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { AppleTvModule } from './apple-tv/apple-tv.module';

@Module({
  imports: [AppleTvModule],
  controllers: [AppController],
  providers: [AppService],
})
export class AppModule {}
